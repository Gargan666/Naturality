package naturality.weather;

import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathFinder;
import net.minecraft.world.phys.Vec3;

/** Temporary ground navigation while inverted; vanilla goals and A* remain in charge. */
public final class CeilingPathNavigation extends GroundPathNavigation {
    public CeilingPathNavigation(Mob mob, Level level, GroundPathNavigation original) {
        super(mob, level);
        var old = original.getNodeEvaluator();
        nodeEvaluator.setCanFloat(old.canFloat());
        nodeEvaluator.setCanOpenDoors(old.canOpenDoors());
        nodeEvaluator.setCanPassDoors(old.canPassDoors());
        nodeEvaluator.setCanWalkOverFences(old.canWalkOverFences());
    }
    @Override protected PathFinder createPathFinder(int maxVisitedNodes) {
        nodeEvaluator = new CeilingNodeEvaluator();
        return new PathFinder(nodeEvaluator, maxVisitedNodes);
    }
    @Override protected Path createPath(Set<BlockPos> targets, int radiusOffset, boolean above, int range, float maxLength) {
        var projected = new java.util.HashSet<BlockPos>();
        for (var target : targets) {
            var surface = surfaceAt(mob, target, 4);
            projected.add(surface == null ? target : BlockPos.containing(surface.position(mob).add(0, 1e-5, 0)));
        }
        return super.createPath(projected, radiusOffset, above, range, maxLength);
    }
    @Override public Path createPath(BlockPos pos, int range) {
        // Skip GroundPathNavigation's search down to the island floor.
        return createPath(Set.of(pos), 8, false, range);
    }
    @Override public Path createPath(Entity target, int range) { return createPath(target.blockPosition(), range); }
    @Override public boolean moveTo(Path path, double speed) {
        if (path != null && path.getNodeCount() > 0 && !(path.getNode(0) instanceof CeilingNodeEvaluator.CeilingNode))
            path = createPath(path.getTarget(), 1);
        return super.moveTo(path, speed);
    }
    @Override protected Vec3 getTempMobPos() { return mob.position(); }
    @Override protected void trimPath() {} // Upright cauldron/sun trimming is inappropriate below a ceiling.
    @Override protected double getGroundY(Vec3 target) {
        return ((CeilingNodeEvaluator.CeilingNode) path.getNextNode()).surface.position(mob).y;
    }
    @Override public void tick() {
        tick++;
        if (hasDelayedRecomputation) recomputePath();
        if (isDone()) return;
        followThePath();
        if (!isDone()) {
            var target = ((CeilingNodeEvaluator.CeilingNode) path.getNextNode()).surface.position(mob);
            mob.getMoveControl().setWantedPosition(target.x, target.y, target.z, speedModifier);
        }
    }
    @Override protected void followThePath() {
        var target = ((CeilingNodeEvaluator.CeilingNode) path.getNextNode()).surface.position(mob);
        maxDistanceToWaypoint = Math.min(.35F, mob.getBbWidth() * .5F);
        if (Math.abs(mob.getX() - target.x) < maxDistanceToWaypoint
                && Math.abs(mob.getZ() - target.z) < maxDistanceToWaypoint && Math.abs(mob.getY() - target.y) < .5)
            path.advance();
        doStuckDetection(mob.position());
    }
    public static CeilingSurface surfaceAt(Mob mob, BlockPos pos, int radius) {
        double offset = Mth.floor(mob.getBbWidth() + 1) * .5;
        double feet = pos.getY() + mob.getBbHeight();
        return CeilingSurface.find(mob.level(), mob, pos.getX() + offset, pos.getZ() + offset,
            feet, feet - radius, feet + radius + 1);
    }
    @Override public boolean isStableDestination(BlockPos pos) {
        var surface = surfaceAt(mob, pos, 0);
        return surface != null && Mth.floor(surface.position(mob).y + 1e-5) == pos.getY();
    }
}
