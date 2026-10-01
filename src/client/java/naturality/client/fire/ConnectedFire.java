package naturality.client.fire;

import naturality.fire.FireGeometry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;

/** Bounded, one-neighbour surface matching. No component flood fills or extra quads. */
public final class ConnectedFire {
    private ConnectedFire() { }

    public static int color(BlockGetter level, BlockPos pos, BlockState state, FireGeometry.Sheet sheet) {
        if (!naturality.config.NaturalityConfig.get().effects.connectedFire) return ProceduralFire.seedColor(pos);
        var tangent = sheet.bottomRight().subtract(sheet.bottomLeft());
        // Rotated boards keep their single fitted sprite; do not join merely adjacent voxels.
        boolean alongX = Math.abs(tangent.z) < 1e-5;
        if (!alongX && Math.abs(tangent.x) > 1e-5) return ProceduralFire.seedColor(pos);
        if (Math.abs(tangent.length() - 1) > 1e-5 || Math.abs(sheet.height() - 1) > 1e-5)
            return ProceduralFire.seedColor(pos);
        Direction right = alongX ? Direction.EAST : Direction.SOUTH;
        Direction[] neighbours = {right.getOpposite(), right, Direction.UP, Direction.DOWN};
        int mask = 0;
        for (int i = 0; i < neighbours.length; i++) {
            Direction direction = neighbours[i];
            BlockPos otherPos = pos.relative(direction);
            var otherState = level.getBlockState(otherPos);
            if (otherState.getBlock() != state.getBlock()) continue;
            for (var other : FireGeometry.sides(level, otherPos, otherState)) {
                if (other.normal().distanceToSqr(sheet.normal()) > 1e-8) continue;
                // Matching local endpoints imply the translated rectangles share precisely one edge.
                if (other.bottomLeft().distanceToSqr(sheet.bottomLeft()) < 1e-8
                        && other.bottomRight().distanceToSqr(sheet.bottomRight()) < 1e-8
                        && Math.abs(other.height() - sheet.height()) < 1e-5) {
                    mask |= 1 << i;
                    break;
                }
            }
        }
        if (mask == 0) return ProceduralFire.seedColor(pos);
        int column = (alongX ? pos.getX() : pos.getZ()) & 31;
        int row = pos.getY() & 31;
        int plane = (alongX ? pos.getZ() : pos.getX()) & 31;
        return 0xFF000000 | mask << 20 | plane << 10 | row << 5 | column;
    }
}
