package naturality.weather;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.CollisionGetter;
import net.minecraft.world.level.pathfinder.NodeEvaluator;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.PathfindingContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Loaded, collision-checked footing beneath a ceiling; Y is the actual foot surface. */
public record CeilingSurface(double x, double feet, double z, float cost) {
    public Vec3 position(Mob mob) { return new Vec3(x, feet - mob.getBbHeight(), z); }
    public AABB box(Mob mob) {
        double half = mob.getBbWidth() / 2.0;
        return new AABB(x - half, feet - mob.getBbHeight(), z - half, x + half, feet, z + half).deflate(1e-5);
    }
    public static CeilingSurface find(CollisionGetter level, Mob mob, double x, double z,
            double preferredFeet, double minFeet, double maxFeet) {
        CeilingSurface best = null;
        var context = new PathfindingContext(level, mob);
        var pos = new BlockPos.MutableBlockPos();
        int low = Math.max(mob.level().getMinY(), Mth.floor(minFeet) - 1);
        int high = Math.min(mob.level().getMaxY(), Mth.floor(maxFeet));
        for (int y = low; y <= high; y++) {
            pos.set(Mth.floor(x), y, Mth.floor(z));
            if (!loaded(mob, pos.getX(), pos.getZ())) continue;
            var state = level.getBlockState(pos);
            var shape = state.getCollisionShape(level, pos);
            for (var part : shape.toAabbs()) {
                double feet = y + part.minY;
                if (feet < minFeet - 1e-6 || feet > maxFeet + 1e-6
                        || x < pos.getX() + part.minX || x > pos.getX() + part.maxX
                        || z < pos.getZ() + part.minZ || z > pos.getZ() + part.maxZ) continue;
                if (best != null && Math.abs(best.feet - preferredFeet) <= Math.abs(feet - preferredFeet)) continue;
                var candidate = new CeilingSurface(x, feet, z, 0);
                var box = candidate.box(mob);
                if (!loaded(mob, box) || !level.noCollision(mob, box)) continue;
                float cost = NodeEvaluator.isBurningBlock(state) ? mob.getPathfindingMalus(PathType.FIRE) : 0;
                boolean safe = cost >= 0;
                for (var cell : BlockPos.betweenClosed(Mth.floor(box.minX), Mth.floor(box.minY), Mth.floor(box.minZ),
                        Mth.floor(box.maxX), Mth.floor(box.maxY), Mth.floor(box.maxZ))) {
                    if (!level.getFluidState(cell).isEmpty()) { safe = false; break; }
                    var type = context.getPathTypeFromState(cell.getX(), cell.getY(), cell.getZ());
                    // Shape clearance, rather than a full-cell BLOCKED label, handles slabs/stairs.
                    if (type == PathType.BLOCKED) continue;
                    float malus = mob.getPathfindingMalus(type);
                    if (malus < 0) { safe = false; break; }
                    cost = Math.max(cost, malus);
                }
                if (safe) best = new CeilingSurface(x, feet, z, cost);
            }
        }
        return best;
    }
    private static boolean loaded(Mob mob, AABB box) {
        return loaded(mob, Mth.floor(box.minX), Mth.floor(box.minZ))
            && loaded(mob, Mth.floor(box.maxX), Mth.floor(box.maxZ));
    }
    private static boolean loaded(Mob mob, int x, int z) {
        return mob.level().getChunkSource().getChunkNow(SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(z)) != null;
    }
    public boolean connects(CollisionGetter level, Mob mob, CeilingSurface next) {
        // Jump away from support, move sideways, then fall toward the new ceiling.
        // Swept body boxes prevent head collisions and cutting through corners.
        double lift = Math.min(feet, next.feet);
        var from = box(mob);
        var to = next.box(mob);
        return level.noCollision(mob, from.expandTowards(0, lift - feet, 0))
            && level.noCollision(mob, from.move(0, lift - feet, 0).expandTowards(next.x - x, 0, next.z - z))
            && level.noCollision(mob, to.expandTowards(0, lift - next.feet, 0));
    }
}
