package naturality.client.fluid;

import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/** Join exposed liquid faces while retaining the inset against block geometry. */
public final class FluidGeometry {
    private FluidGeometry() {}

    public static float connectedHeight(BlockAndTintGetter level, BlockPos pos, float height) {
        int local = pos.getY() & 15;
        float relative = height - local;
        if (Math.abs(relative - 0.001F) < 0.0001F && level.getBlockState(pos.below()).isAir())
            return local;
        // A full-height corner can be lowered by vanilla's top-face epsilon,
        // leaving a crack against a neighboring column at the integer boundary.
        if (Math.abs(relative - 0.999F) < 0.0001F && level.getBlockState(pos.above()).isAir())
            return local + 1;
        return height;
    }

    public static float connectedCoordinate(BlockAndTintGetter level, BlockPos pos, float coordinate, boolean xAxis) {
        int local = (xAxis ? pos.getX() : pos.getZ()) & 15;
        float relative = coordinate - local;
        Direction side;
        float boundary;
        if (Math.abs(relative - 0.001F) < 0.0001F) {
            side = xAxis ? Direction.WEST : Direction.NORTH;
            boundary = local;
        } else if (Math.abs(relative - 0.999F) < 0.0001F) {
            side = xAxis ? Direction.EAST : Direction.SOUTH;
            boundary = local + 1;
        } else return coordinate;
        return level.getBlockState(pos.relative(side)).isAir() ? boundary : coordinate;
    }
}
