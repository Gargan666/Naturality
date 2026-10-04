package naturality.weather;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.VegetationBlock;
import net.minecraft.world.phys.Vec3;
/** Shared geometric shelter probes for rendering and physical wind. */
public final class WindShelter {
    private static final int[] EXPOSURE_DISTANCES={4,8};
    private static final int[] EXPOSURE_HEIGHTS={3};
    public static boolean hasOpenSkyPath(net.minecraft.world.level.BlockGetter level,
            net.minecraft.core.BlockPos pos, java.util.function.ToIntFunction<net.minecraft.core.BlockPos> sky) {
        if (sky.applyAsInt(pos) <= 0) return false;
        Vec3 origin = new Vec3(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5);
        if (openSkyColumn(level, origin, sky)) return true;
        for (int distance : EXPOSURE_DISTANCES) for (int dy : EXPOSURE_HEIGHTS)
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
                if ((dx == 0 && dz == 0) || Math.max(Math.abs(dx), Math.abs(dz)) != 1) continue;
                Vec3 target = origin.add(dx * distance, dy, dz * distance);
                var targetPos = net.minecraft.core.BlockPos.containing(target);
                if (sky.applyAsInt(targetPos.above(5)) <= 0) continue;
                if (passable(level, targetPos) && openSkyColumn(level, target, sky) && clearRay(level, origin, target)) return true;
            }
        return false;
    }
    private static boolean openSkyColumn(net.minecraft.world.level.BlockGetter level, Vec3 origin, java.util.function.ToIntFunction<net.minecraft.core.BlockPos> sky) {
        Vec3 top = origin.add(0, 5, 0);
        var topPos = net.minecraft.core.BlockPos.containing(top);
        return sky.applyAsInt(topPos) > 0 && passable(level, topPos)
            && clearRay(level, origin, top);
    }
    private static boolean passable(net.minecraft.world.level.BlockGetter level,
            net.minecraft.core.BlockPos pos) {
        var state = level.getBlockState(pos);
        return state.is(BlockTags.LEAVES) || state.getBlock() instanceof VegetationBlock
            || state.getCollisionShape(level, pos).isEmpty();
    }
    private static boolean clearRay(net.minecraft.world.level.BlockGetter level, Vec3 from, Vec3 to) {
        double span = Math.max(Math.max(Math.abs(to.x - from.x), Math.abs(to.y - from.y)), Math.abs(to.z - from.z));
        int steps = Math.max(1, (int)Math.ceil(span * 4));
        net.minecraft.core.BlockPos previous = null;
        for (int i = 1; i < steps; i++) {
            var sample = from.lerp(to, i / (double)steps);
            var pos = net.minecraft.core.BlockPos.containing(sample);
            if (previous != null && pos.equals(previous)) continue;
            previous = pos;
            var state = level.getBlockState(pos);
            if (state.is(BlockTags.LEAVES) || state.getBlock() instanceof VegetationBlock) continue;
            var shape = state.getCollisionShape(level, pos);
            if (!shape.isEmpty() && shape.clip(from, to, pos) != null) return false;
        }
        return true;
    }
}
