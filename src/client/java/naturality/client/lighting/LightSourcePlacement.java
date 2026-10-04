package naturality.client.lighting;

import java.util.ArrayList;
import java.util.List;
import naturality.util.LoadedChunks;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import org.jspecify.annotations.Nullable;

/** Keep emitters on the owner's side of actual collision geometry, including leaves and slabs. */
public final class LightSourcePlacement {
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
        for (AABB box : boxes(level, point, 1)) {
            if (box.inflate(CLEARANCE * 0.5).contains(point)) return false;
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
            var shape = state.getLightDampening() >= 15 ? Shapes.block() : state.getCollisionShape(level, pos);
            for (AABB box : shape.toAabbs()) result.add(box.move(pos));
        }
        return result;
    }
}
