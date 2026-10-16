package naturality.client.rendering.lighting.dynamic;

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
import java.util.ArrayList;
import java.util.List;
import naturality.api.worlds.LoadedChunks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import org.jspecify.annotations.Nullable;

/** Lightmap precision is 1/16 of a light level. Keep it through propagation. */
@org.jspecify.annotations.NullMarked
public final class FractionalLightField {
    private static final Direction[] DIRECTIONS = Direction.values();
    private final ClientLevel level;
    private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
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
        for (int value = 240; value > 16; value--) {
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
        // These cells remain lit, but cannot pass even one minimum-cost edge.
        if (value <= 16) return;
        if (queues[value] == null) queues[value] = new LongArrayFIFOQueue();
        queues[value].enqueue(position);
    }

    private BlockState block(long key) {
        var cached = blocks.get(key);
        if (cached != null) return cached;
        var pos = cursor.set(BlockPos.getX(key), BlockPos.getY(key), BlockPos.getZ(key));
        // Build limits constrain terrain, not entity light. Loaded columns have
        // open air above/below their terrain and can still contain flying entities.
        var chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        var result = chunk == null ? Blocks.BEDROCK.defaultBlockState() : chunk.getBlockState(pos);
        blocks.put(key, result);
        return result;
    }

    /** Keep emitters on the owner's side of actual collision geometry, including leaves and slabs. */
    public static final class LightSourcePlacement {
        private static final double CLEARANCE = 1.0 / 64.0;

        private LightSourcePlacement() {}

        public static @Nullable Vec3 resolve(ClientLevel level, Vec3 desired, Vec3 owner) {
            Vec3 anchor = pushOut(level, owner);
            if (anchor == null) return null;
            var hit = level.clip(new ClipContext(anchor, desired, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, CollisionContext.empty()));
            if (hit.getType() != HitResult.Type.MISS) {
                var normal = hit.getDirection();
                desired = hit.getLocation().add(normal.getStepX() * CLEARANCE,
                    normal.getStepY() * CLEARANCE, normal.getStepZ() * CLEARANCE);
            }
            Vec3 result = pushOut(level, desired);
            return result == null ? anchor : result;
        }

        private static @Nullable Vec3 pushOut(ClientLevel level, Vec3 point) {
            if (!LoadedChunks.has(level, BlockPos.containing(point))) return null;
            if (clear(level, point)) return point;
            // Embedded bodies (items, burning mobs, or a suffocating owner) need an
            // exposed face, not another point in the same solid cell. Search locally;
            // unavailable chunks are never loaded to find an escape.
            var candidates = new ArrayList<Vec3>();
            for (AABB box : boxes(level, point, 2)) {
                candidates.add(new Vec3(box.minX - CLEARANCE, point.y, point.z));
                candidates.add(new Vec3(box.maxX + CLEARANCE, point.y, point.z));
                candidates.add(new Vec3(point.x, box.minY - CLEARANCE, point.z));
                candidates.add(new Vec3(point.x, box.maxY + CLEARANCE, point.z));
                candidates.add(new Vec3(point.x, point.y, box.minZ - CLEARANCE));
                candidates.add(new Vec3(point.x, point.y, box.maxZ + CLEARANCE));
            }
            candidates.sort(java.util.Comparator.comparingDouble(point::distanceToSqr));
            for (Vec3 candidate : candidates) {
                if (point.distanceToSqr(candidate) <= 9 && LoadedChunks.has(level, BlockPos.containing(candidate))
                        && clear(level, candidate)) return candidate;
            }
            return null;
        }

        private static boolean clear(ClientLevel level, Vec3 point) {
            BlockPos center = BlockPos.containing(point);
            double pad = CLEARANCE * 0.5;
            for (BlockPos pos : BlockPos.betweenClosed(center.offset(-1, -1, -1), center.offset(1, 1, 1))) {
                double x = point.x - pos.getX(), y = point.y - pos.getY(), z = point.z - pos.getZ();
                if (!LoadedChunks.has(level, pos)) {
                    if (x >= -pad && x < 1 + pad && y >= -pad && y < 1 + pad && z >= -pad && z < 1 + pad) return false;
                    continue;
                }
                var state = level.getBlockState(pos);
                if (state.isAir()) continue;
                var shape = state.getLightDampening() >= 15 ? Shapes.block() : state.getCollisionShape(level, pos);
                for (AABB box : shape.toAabbs()) {
                    // Keep vanilla's inclusive minimum/exclusive maximum containment.
                    if (point.x >= box.minX + pos.getX() - pad && point.x < box.maxX + pos.getX() + pad
                            && point.y >= box.minY + pos.getY() - pad && point.y < box.maxY + pos.getY() + pad
                            && point.z >= box.minZ + pos.getZ() - pad && point.z < box.maxZ + pos.getZ() + pad) return false;
                }
            }
            return true;
        }

        private static List<AABB> boxes(ClientLevel level, Vec3 point, int radius) {
            var result = new ArrayList<AABB>();
            BlockPos center = BlockPos.containing(point);
            for (BlockPos pos : BlockPos.betweenClosed(center.offset(-radius, -radius, -radius),
                    center.offset(radius, radius, radius))) {
                if (!LoadedChunks.has(level, pos)) {
                    result.add(new AABB(pos));
                    continue;
                }
                var state = level.getBlockState(pos);
                if (state.isAir()) continue;
                var shape = state.getLightDampening() >= 15 ? Shapes.block() : state.getCollisionShape(level, pos);
                for (AABB box : shape.toAabbs()) result.add(box.move(pos));
            }
            return result;
        }
    }
}
