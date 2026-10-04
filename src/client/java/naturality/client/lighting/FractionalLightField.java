package naturality.client.lighting;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LightEngine;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

/** Lightmap precision is 1/16 of a light level. Keep it through propagation. */
public final class FractionalLightField {
    private static final Direction[] DIRECTIONS = Direction.values();
    private final ClientLevel level;
    private final Long2ObjectOpenHashMap<BlockState> blocks = new Long2ObjectOpenHashMap<>();
    private final LongArrayFIFOQueue[] queues = new LongArrayFIFOQueue[241];
    private final Long2IntOpenHashMap seeds = new Long2IntOpenHashMap();
    private final Long2IntOpenHashMap light = new Long2IntOpenHashMap();

    public FractionalLightField(ClientLevel level) { this.level = level; }

    public void add(Vec3 source, int strength) {
        int x = Mth.floor(source.x - 0.5), y = Mth.floor(source.y - 0.5), z = Mth.floor(source.z - 0.5);
        for (int dx = 0; dx <= 1; dx++) for (int dy = 0; dy <= 1; dy++) for (int dz = 0; dz <= 1; dz++) {
            BlockPos pos = new BlockPos(x + dx, y + dy, z + dz);
            Vec3 center = Vec3.atCenterOf(pos);
            int value = seedStrength(source, center, strength);
            if (value <= 0 || block(pos.asLong()).getLightDampening() >= 15) continue;
            if (level.clip(new ClipContext(source, center, ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE, CollisionContext.empty())).getType() != HitResult.Type.MISS) continue;
            long key = pos.asLong();
            seeds.put(key, Math.max(seeds.get(key), value));
            offer(key, value);
        }
    }

    public static int seedStrength(Vec3 source, Vec3 center, int strength) {
        double distance = Math.abs(source.x - center.x) + Math.abs(source.y - center.y) + Math.abs(source.z - center.z);
        return Math.clamp((int)Math.round((strength - distance) * 16), 0, 240);
    }

    public void addBlock(BlockPos source, int strength) {
        long key = source.asLong();
        int value = Math.clamp(strength * 16, 0, 240);
        seeds.put(key, Math.max(seeds.get(key), value));
        offer(key, value);
    }

    public Long2IntOpenHashMap seeds() { return seeds; }

    public Long2IntOpenHashMap propagate() {
        for (int value = 240; value > 0; value--) {
            var queue = queues[value];
            if (queue == null) continue;
            while (!queue.isEmpty()) {
                long from = queue.dequeueLong();
                if (light.get(from) != value) continue;
                BlockState fromState = block(from);
                for (Direction direction : DIRECTIONS) {
                    long to = BlockPos.offset(from, direction);
                    if (light.get(to) >= value - 16) continue;
                    BlockState toState = block(to);
                    int attenuation = LightEngine.getLightDampeningInto(fromState, toState, direction,
                        Math.max(1, toState.getLightDampening()));
                    offer(to, value - 16 * attenuation);
                }
            }
        }
        return light;
    }

    private void offer(long position, int value) {
        if (value <= light.get(position)) return;
        light.put(position, value);
        if (queues[value] == null) queues[value] = new LongArrayFIFOQueue();
        queues[value].enqueue(position);
    }

    private BlockState block(long key) {
        var cached = blocks.get(key);
        if (cached != null) return cached;
        var pos = BlockPos.of(key);
        // Build limits constrain terrain, not entity light. Loaded columns have
        // open air above/below their terrain and can still contain flying entities.
        var result = !naturality.util.LoadedChunks.has(level, pos)
            ? Blocks.BEDROCK.defaultBlockState() : level.getBlockState(pos);
        blocks.put(key, result);
        return result;
    }
}
