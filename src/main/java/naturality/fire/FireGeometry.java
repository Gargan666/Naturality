package naturality.fire;

import java.util.ArrayList;
import java.util.List;
import naturality.snow.ShapeRecursionGuard;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** One geometry definition for rendered flames, selection and entity contact. */
public final class FireGeometry {
    public static final ThreadLocal<net.minecraft.world.level.ClipContext> PICK_CONTEXT = new ThreadLocal<>();
    // Vanilla fire frames leave about three of sixteen rows transparent at
    // the top. 1.55 model units yields about 1.25 visible units on average.
    public static final float HEIGHT = 24.8F / 16;
    public static final double FLOOR_HITBOX_HEIGHT = 1.0 / 16;
    public static final double INSET = 1.0 / 512;
    public static final double CONTACT_DEPTH = 1.0 / 16;
    public record Sheet(Vec3 bottomLeft, Vec3 bottomRight, double height, Vec3 normal) { }
    private FireGeometry() { }

    /** Native floor models use 22.4-pixel tilted sheets; fitted narrow flames retain their taller proportions. */
    public static double floorHeight(FireSurface.Patch patch) {
        double width = Math.hypot(patch.ux(), patch.uz());
        double length = Math.hypot(patch.vx(), patch.vz());
        if (width >= 1.0 - 1e-5 && length >= 1.0 - 1e-5) return 22.4 / 16.0;
        return HEIGHT * (width + length) / 2;
    }

    public static List<AABB> supportBoxes(BlockGetter level, BlockPos pos) {
        var state = level.getBlockState(pos);
        if (state.isAir() || state.is(Blocks.SNOW) || state.is(Blocks.SNOW_BLOCK)
                || state.getBlock() instanceof BaseFireBlock) return List.of();
        var boxes = new ArrayList<AABB>();
        if (state.getBlock() instanceof FenceBlock) {
            boxes.add(box(6, 0, 6, 10, 16, 10));
            for (var direction : Direction.Plane.HORIZONTAL) {
                if (!state.getValue(FenceBlock.PROPERTY_BY_DIRECTION.get(direction))) continue;
                for (int y : new int[]{6, 12}) {
                    AABB rail = box(7, y, 0, 9, y + 3, 6);
                    boxes.add(rotate(rail, direction));
                }
            }
        } else if (state.getBlock() instanceof FenceGateBlock) {
            boxes.add(box(0, 5, 7, 2, 16, 9));
            boxes.add(box(14, 5, 7, 16, 16, 9));
            if (state.getValue(FenceGateBlock.OPEN)) {
                for (int x : new int[]{0, 14}) {
                    boxes.add(box(x, 6, 13, x + 2, 15, 15));
                    boxes.add(box(x, 6, 9, x + 2, 9, 13));
                    boxes.add(box(x, 12, 9, x + 2, 15, 13));
                }
            } else {
                boxes.add(box(6, 6, 7, 10, 15, 9));
                for (int x : new int[]{2, 10}) for (int y : new int[]{6, 12}) boxes.add(box(x, y, 7, x + 4, y + 3, 9));
            }
            Direction facing = state.getValue(FenceGateBlock.FACING);
            double shift = state.getValue(FenceGateBlock.IN_WALL) ? -3.0 / 16 : 0;
            // The model template faces south; rotate() takes a north-based direction.
            return boxes.stream().map(b -> rotate(b, facing.getOpposite()).move(0, shift, 0)).toList();
        } else if (state.getBlock() instanceof WallSignBlock) {
            boxes.add(rotate(box(0, 13.0 / 3, 1.0 / 3, 16, 37.0 / 3, 5.0 / 3),
                state.getValue(WallSignBlock.FACING).getOpposite()));
        } else if (state.getBlock() instanceof StandingSignBlock sign) {
            // One enclosing box for queries; rendering uses the oriented board directly.
            double angle = Math.toRadians(-sign.getYRotationDegrees(state));
            double ux = Math.cos(angle), uz = -Math.sin(angle), vx = -uz / 12, vz = ux / 12;
            var patch = new FireSurface.Patch((float) (0.5 - (ux + vx) / 2), 0,
                (float) (0.5 - (uz + vz) / 2), (float) ux, (float) uz, (float) vx, (float) vz);
            boxes.addAll(prism(patch, 7.0 / 12, 13.0 / 12).toAabbs());
        } else {
            boxes.addAll(state.getShape(level, pos).toAabbs());
        }
        return boxes;
    }

    private static AABB box(double x0, double y0, double z0, double x1, double y1, double z1) {
        return new AABB(x0 / 16, y0 / 16, z0 / 16, x1 / 16, y1 / 16, z1 / 16);
    }
    private static AABB rotate(AABB b, Direction northTo) {
        return switch (northTo) {
            case EAST -> new AABB(1 - b.maxZ, b.minY, b.minX, 1 - b.minZ, b.maxY, b.maxX);
            case SOUTH -> new AABB(1 - b.maxX, b.minY, 1 - b.maxZ, 1 - b.minX, b.maxY, 1 - b.minZ);
            case WEST -> new AABB(b.minZ, b.minY, 1 - b.maxX, b.maxZ, b.maxY, 1 - b.minX);
            default -> b;
        };
    }

    public static List<Sheet> sides(BlockGetter level, BlockPos pos, BlockState state) {
        var result = new ArrayList<Sheet>();
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            var property = FireBlock.PROPERTY_BY_DIRECTION.get(direction);
            if (!state.hasProperty(property) || !state.getValue(property)) continue;
            var support = level.getBlockState(pos.relative(direction));
            if (support.getBlock() instanceof StandingSignBlock signBlock) {
                double angle = Math.toRadians(-signBlock.getYRotationDegrees(support));
                Vec3 tangent = new Vec3(Math.cos(angle), 0, -Math.sin(angle));
                Vec3 normal = new Vec3(-tangent.z, 0, tangent.x);
                Vec3 towardFire = new Vec3(-direction.getStepX(), 0, -direction.getStepZ());
                if (normal.dot(towardFire) < 0) normal = normal.scale(-1);
                Vec3 center = new Vec3(direction.getStepX() + 0.5, 7.0 / 12,
                    direction.getStepZ() + 0.5).add(normal.scale(1.0 / 24 + INSET));
                result.add(new Sheet(center.subtract(tangent.scale(0.5)),
                    center.add(tangent.scale(0.5)), 0.5, normal));
                continue;
            }
            var projected = new ArrayList<FireSurface.Patch>();
            Direction outward = direction.getOpposite();
            boolean alongX = direction.getAxis() == Direction.Axis.X;
            int sign = alongX ? outward.getStepX() : outward.getStepZ();
            for (var b : supportBoxes(level, pos.relative(direction))) {
                double depth = alongX ? (sign > 0 ? b.maxX : b.minX) : (sign > 0 ? b.maxZ : b.minZ);
                double start = alongX ? b.minZ : b.minX;
                double end = alongX ? b.maxZ : b.maxX;
                projected.add(new FireSurface.Patch((float) start, (float) (depth * sign), (float) b.minY,
                    (float) (end - start), 0, 0, (float) (b.maxY - b.minY)));
            }
            for (var p : FireSurface.exposed(projected)) {
                double depth = p.y() * sign + (alongX ? direction.getStepX() : direction.getStepZ()) + sign * INSET;
                Vec3 a = alongX ? new Vec3(depth, p.z(), p.x()) : new Vec3(p.x(), p.z(), depth);
                Vec3 b = alongX ? new Vec3(depth, p.z(), p.x() + p.ux()) : new Vec3(p.x() + p.ux(), p.z(), depth);
                result.add(new Sheet(a, b, p.vz(), new Vec3(outward.getStepX(), 0, outward.getStepZ())));
            }
        }
        return List.copyOf(result);
    }

    public static List<FireSurface.Patch> floors(BlockGetter level, BlockPos pos, BlockState state) {
        if (!FireSurface.isFloorFire(state)) return List.of();
        var tops = FireSurface.below(level, pos);
        return tops.isEmpty() ? List.of(new FireSurface.Patch(0, 0, 0, 1, 0, 0, 1)) : tops;
    }

    public static VoxelShape shape(BlockGetter level, BlockPos pos, BlockState state) {
        if (!ShapeRecursionGuard.enterFire()) return vanillaShape(state);
        try {
        if (level.getBlockState(pos.below()).is(Blocks.SNOW)
                || level.getBlockState(pos.below()).is(Blocks.SNOW_BLOCK))
            return vanillaShape(state);
        VoxelShape result = Shapes.empty();
        for (var p : floors(level, pos, state)) result = Shapes.or(result, prism(p, p.y(), p.y() + FLOOR_HITBOX_HEIGHT));
        for (var side : sides(level, pos, state)) {
            Vec3 a = side.bottomLeft, b = side.bottomRight;
            double nx = side.normal.x * CONTACT_DEPTH, nz = side.normal.z * CONTACT_DEPTH;
            result = Shapes.or(result, Shapes.box(Math.min(a.x, b.x) + Math.min(0, nx), a.y,
                Math.min(a.z, b.z) + Math.min(0, nz), Math.max(a.x, b.x) + Math.max(0, nx), a.y + side.height,
                Math.max(a.z, b.z) + Math.max(0, nz)));
        }
        if (state.hasProperty(FireBlock.UP) && state.getValue(FireBlock.UP)) {
            for (var b : supportBoxes(level, pos.above())) {
                result = Shapes.or(result, Shapes.box(b.minX, b.minY + 1 - CONTACT_DEPTH, b.minZ, b.maxX, b.minY + 1, b.maxZ));
            }
        }
        return result.optimize();
        } finally {
            ShapeRecursionGuard.exitFire();
        }
    }

    private static VoxelShape vanillaShape(BlockState state) {
        if (state.getBlock() instanceof FireBlock fire)
            return ((naturality.mixin.FireShapeAccessor) fire).naturality$vanillaShapes().apply(state);
        // Soul fire inherits BaseFireBlock's one-pixel-high column.
        return Shapes.box(0, 0, 0, 1, 1.0 / 16, 1);
    }

    private static VoxelShape prism(FireSurface.Patch p, double bottom, double top) {
        double minX = Math.min(Math.min(p.x(), p.x() + p.ux()), Math.min(p.x() + p.vx(), p.x() + p.ux() + p.vx()));
        double maxX = Math.max(Math.max(p.x(), p.x() + p.ux()), Math.max(p.x() + p.vx(), p.x() + p.ux() + p.vx()));
        double minZ = Math.min(Math.min(p.z(), p.z() + p.uz()), Math.min(p.z() + p.vz(), p.z() + p.uz() + p.vz()));
        double maxZ = Math.max(Math.max(p.z(), p.z() + p.uz()), Math.max(p.z() + p.vz(), p.z() + p.uz() + p.vz()));
        // Minecraft selection shapes cannot rotate. One conservative AABB avoids
        // dozens of slices and expensive repeated voxel boolean operations.
        return Shapes.box(minX, bottom, minZ, maxX, top, maxZ);
    }
}
