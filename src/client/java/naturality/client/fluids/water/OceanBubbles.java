package naturality.client.fluids.water;

import java.util.HashMap;
import java.util.Map;
import naturality.api.worlds.LoadedChunks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/** Local, bounded bubble sources; no world edits or server-side particle traffic. */
@org.jspecify.annotations.NullMarked
public final class OceanBubbles {
    private static final Map<BlockPos, Integer> SOURCES = new HashMap<>();
    private static @Nullable ClientLevel sourceLevel;

    private OceanBubbles() { }

    private static boolean coral(BlockState state) {
        return state.is(Blocks.TUBE_CORAL) || state.is(Blocks.TUBE_CORAL_FAN)
            || state.is(Blocks.TUBE_CORAL_WALL_FAN) || state.is(Blocks.TUBE_CORAL_BLOCK);
    }

    private static boolean sediment(BlockState state) {
        return state.is(Blocks.DIRT) || state.is(Blocks.SAND) || state.is(Blocks.GRAVEL);
    }

    private static void world(@Nullable ClientLevel level) {
        if (sourceLevel != level) {
            SOURCES.clear();
            sourceLevel = level;
        }
    }

    /** Vanilla's bounded ambient sampling discovers sources without scanning chunks. */
    public static void sample(ClientLevel level, BlockPos pos) {
        world(level);
        if (SOURCES.size() >= 128 || !LoadedChunks.has(level, pos) || SOURCES.containsKey(pos)) return;
        BlockState state = level.getBlockState(pos);
        if (coral(state)) {
            SOURCES.put(pos.immutable(), -1);
        } else if (sediment(state) && level.getRandom().nextInt(960) == 0
                && level.getFluidState(pos.above()).is(FluidTags.WATER)
                && level.getFluidState(pos.above(2)).is(FluidTags.WATER)
                && level.getBiome(pos).is(BiomeTags.IS_OCEAN)
                && SOURCES.values().stream().filter(time -> time >= 0).count() < 8) {
            SOURCES.put(pos.immutable(), 40 + level.getRandom().nextInt(161));
        }
    }

    public static void tick(Minecraft client) {
        ClientLevel level = client.level;
        world(level);
        var player = client.player;
        if (level == null || player == null || client.isPaused()) return;
        var iterator = SOURCES.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            BlockPos pos = entry.getKey();
            int remaining = entry.getValue();
            if (!LoadedChunks.has(level, pos) || pos.distSqr(player.blockPosition()) > 48 * 48
                    || (remaining < 0 ? !coral(level.getBlockState(pos)) : !sediment(level.getBlockState(pos)))
                    || remaining == 0) {
                iterator.remove();
                continue;
            }
            if (remaining > 0) entry.setValue(remaining - 1);
            BlockState state = level.getBlockState(pos);
            // Fans and solid coral blocks emit one fifth as often as the Tube Coral plant.
            int emissionInterval = remaining < 0 ? (state.is(Blocks.TUBE_CORAL) ? 8 : 40) : 3;
            if (level.getRandom().nextInt(emissionInterval) != 0) continue;
            double y = pos.getY() + (state.isCollisionShapeFullBlock(level, pos) ? 1.04 : 0.55);
            bubble(level, pos.getX() + 0.35 + level.getRandom().nextDouble() * 0.3, y,
                pos.getZ() + 0.35 + level.getRandom().nextDouble() * 0.3, 0, 0.05, 0, true);
        }
    }

    /** Compare the lid's two tick endpoints so bursts occur once per transition. */
    public static void chest(ClientLevel level, BlockPos pos, BlockState state, ChestBlockEntity chest) {
        var player = Minecraft.getInstance().player;
        if (player == null || pos.distSqr(player.blockPosition()) > 32 * 32) return;
        float previous = chest.getOpenNess(0);
        float current = chest.getOpenNess(1);
        boolean opening = previous == 0 && current > 0;
        boolean closing = previous > 0 && current == 0;
        if (!opening && !closing && (current <= 0 || level.getRandom().nextInt(5) != 0)) return;
        boolean burst = opening || closing;
        int count = opening ? 16 : closing ? 7 : 1;
        double minX = 0.125, maxX = 0.875, minZ = 0.125, maxZ = 0.875;
        Direction seam = state.getValue(ChestBlock.TYPE) == net.minecraft.world.level.block.state.properties.ChestType.SINGLE
            ? null : ChestBlock.getConnectedDirection(state);
        if (seam == Direction.WEST) minX = 0;
        if (seam == Direction.EAST) maxX = 1;
        if (seam == Direction.NORTH) minZ = 0;
        if (seam == Direction.SOUTH) maxZ = 1;
        Direction front = state.getValue(ChestBlock.FACING);
        Direction[] sides = seam == null
            ? new Direction[] {front, front.getClockWise(), front.getCounterClockWise()}
            : new Direction[] {front, seam.getOpposite()};
        for (int i = 0; i < count; i++) {
            double x = minX + level.getRandom().nextDouble() * (maxX - minX);
            double z = minZ + level.getRandom().nextDouble() * (maxZ - minZ);
            double vx = 0, vz = 0;
            if (burst) {
                // Emit from the front and exposed sides, never the hinge edge or double-chest seam.
                Direction side = sides[i % sides.length];
                if (side == Direction.WEST) x = 0.065;
                if (side == Direction.EAST) x = 0.935;
                if (side == Direction.NORTH) z = 0.065;
                if (side == Direction.SOUTH) z = 0.935;
                double speed = 5 * (0.12 + level.getRandom().nextDouble() * 0.12);
                vx = side.getStepX() * speed;
                vz = side.getStepZ() * speed;
            }
            // The open stream covers the hollow interior floor rather than the rim.
            bubble(level, pos.getX() + x, pos.getY() + (burst ? 0.62 : 0.18), pos.getZ() + z,
                vx, (0.05 + level.getRandom().nextDouble() * 0.02) * (burst ? 5 : 1), vz, !burst);
        }
    }

    // Particle creation is synchronous on the client thread; restore the context even on failure.
    /** Smooth, independently phased currents avoid frame-to-frame random jitter. */
    public static final class BubbleMotion {
        private final double phase = Math.random() * Math.PI * 2;
        private final double frequency = 0.07 + Math.random() * 0.07;
        private final double strength = 0.0015 + Math.random() * 0.0015;
        private int ticks;

        public void tick() { ticks++; }

        public double driftX() {
            return strength * (Math.sin(phase + ticks * frequency)
                + 0.4 * Math.sin(phase * 2.3 + ticks * frequency * 0.43));
        }

        public double driftZ() {
            return strength * (Math.cos(phase * 1.7 + ticks * frequency * 0.83)
                + 0.4 * Math.sin(phase + ticks * frequency * 0.37));
        }

        public double riseSpeed() {
            return 0.035 + 0.007 * Math.sin(phase + ticks * frequency * 0.6);
        }
    }

    private static boolean spawningLongBubble;
    private static boolean spawningBurstBubble;

    public static boolean spawningBurstBubble() {
        return spawningBurstBubble;
    }

    public static boolean spawningLongBubble() {
        return spawningLongBubble;
    }
    private static void bubble(ClientLevel level, double x, double y, double z, double vx, double vy, double vz, boolean longLived) {
        BlockPos pos = BlockPos.containing(x, y, z);
        if (LoadedChunks.has(level, pos) && level.getFluidState(pos).is(FluidTags.WATER)) {
            boolean previous = spawningLongBubble;
            boolean previousBurst = spawningBurstBubble;
            spawningLongBubble = longLived;
            spawningBurstBubble = !longLived;
            try {
                level.addParticle(ParticleTypes.BUBBLE, x, y, z, vx, vy, vz);
            } finally {
                spawningLongBubble = previous;
                spawningBurstBubble = previousBurst;
            }
        }
    }
}
