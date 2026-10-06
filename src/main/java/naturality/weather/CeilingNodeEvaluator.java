package naturality.weather;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.pathfinder.*;

public final class CeilingNodeEvaluator extends NodeEvaluator {
    public static final class CeilingNode extends Node {
        public final CeilingSurface surface;
        CeilingNode(int x, int y, int z, CeilingSurface surface) {
            super(x, y, z);
            this.surface = surface;
            type = PathType.WALKABLE;
            costMalus = surface.cost();
        }
    }
    private CeilingNode node(CeilingSurface surface) {
        int x = Mth.floor(surface.x() - entityWidth * .5);
        int y = Mth.floor(surface.feet() - mob.getBbHeight() + 1e-5);
        int z = Mth.floor(surface.z() - entityDepth * .5);
        return (CeilingNode) nodes.computeIfAbsent(Node.createHash(x, y, z), key -> new CeilingNode(x, y, z, surface));
    }
    @Override public Node getStart() {
        var box = mob.getBoundingBox();
        var actual = new CeilingSurface(mob.getX(), box.maxY, mob.getZ(), 0);
        CeilingSurface best = null;
        for (int x = Mth.floor(box.minX); x <= Mth.floor(box.maxX); x++)
            for (int z = Mth.floor(box.minZ); z <= Mth.floor(box.maxZ); z++) {
                var surface = CeilingSurface.find(currentContext.level(), mob, x + entityWidth * .5,
                    z + entityDepth * .5, box.maxY, box.maxY - .6, box.maxY + .6);
                if (surface != null && actual.connects(currentContext.level(), mob, surface)
                        && (best == null || surface.position(mob).distanceToSqr(mob.position()) < best.position(mob).distanceToSqr(mob.position())))
                    best = surface;
            }
        return best == null ? null : node(best);
    }
    @Override public Target getTarget(double x, double y, double z) {
        // Navigation projects and deduplicates destinations before A* constructs its target map.
        return new Target(Mth.floor(x), Mth.floor(y), Mth.floor(z));
    }
    @Override public int getNeighbors(Node[] neighbors, Node current) {
        var from = ((CeilingNode) current).surface;
        int count = 0;
        for (var direction : Direction.Plane.HORIZONTAL) {
            var next = CeilingSurface.find(currentContext.level(), mob, from.x() + direction.getStepX(),
                from.z() + direction.getStepZ(), from.feet(), from.feet() - Math.max(1.125, mob.maxUpStep()),
                from.feet() + Math.min(4, mob.getMaxFallDistance()));
            if (next == null || !from.connects(currentContext.level(), mob, next)) continue;
            var node = node(next);
            if (!node.closed) neighbors[count++] = node;
        }
        return count;
    }
    @Override public PathType getPathType(Mob mob, BlockPos pos) {
        var surface = CeilingPathNavigation.surfaceAt(mob, pos, 1);
        return surface == null ? PathType.BLOCKED : surface.cost() > 0 ? PathType.FIRE_IN_NEIGHBOR : PathType.WALKABLE;
    }
    @Override public PathType getPathTypeOfMob(PathfindingContext context, int x, int y, int z, Mob mob) {
        return getPathType(mob, new BlockPos(x, y, z));
    }
    @Override public PathType getPathType(PathfindingContext context, int x, int y, int z) {
        return mob == null ? PathType.BLOCKED : getPathType(mob, new BlockPos(x, y, z));
    }
}
