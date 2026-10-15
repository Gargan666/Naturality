package naturality.systems.gravity;

import naturality.effects.distortion.DistortionState;
import naturality.systems.weather.end.framework.EndWeatherSystem;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import naturality.bootstrap.ContentRegistration.NaturalityTags;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.Level;
import naturality.api.worlds.LoadedChunks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.monster.Enderman;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import java.util.Set;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathFinder;
import net.minecraft.world.level.pathfinder.*;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.CollisionGetter;
import net.minecraft.world.level.pathfinder.NodeEvaluator;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.PathfindingContext;
import net.minecraft.world.phys.AABB;

@org.jspecify.annotations.NullMarked
public final class EndGravity {
    private EndGravity() {}
    private static boolean distorted(Entity entity) {
        return naturality.config.startup.FeatureConfig.Feature.DISTORTION.enabled() && entity instanceof LivingEntity living
            && (living.hasEffect(naturality.bootstrap.ContentRegistration.NaturalityEffects.DISTORTION)
                || living instanceof DistortionState state && state.naturality$distortion(false)>0);
    }
    public static boolean affected(Entity entity) {
        return (naturality.config.startup.FeatureConfig.Feature.END_WEATHER.enabled() && entity.level().dimension().equals(Level.END) || distorted(entity))
            && !BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(entity.getType()).is(NaturalityTags.NO_END_GRAVITY)
            && !entity.isNoGravity() && !entity.isPassenger()
            && !(entity instanceof Player p && (p.isSpectator() || p.getAbilities().flying && !distorted(p)));
    }
    public static float factor(Entity entity) {
        return affected(entity) ? EndWeatherSystem.state(entity.level()).strength()*(float)Math.cos(Math.PI*inversion(entity,false)) : 1;
    }
    public static float inversion(Entity entity, boolean render) {
        return affected(entity) && entity instanceof DistortionState state ? state.naturality$distortion(render) : 0;
    }
    public static boolean inverted(Entity entity) { return inversion(entity,false) > .5F; }
    public static float eyeHeight(Entity entity, float height, boolean render) {
        float half = entity.getBbHeight()/2;
        return half + (height-half)*(float)Math.cos(Math.PI*inversion(entity,render));
    }
    /** A soft ceiling for upward falls, 16 blocks below the dimension's build limit. */
    public static double upwardLimit(Entity entity) { return entity.level().getMaxY()-16; }
    public static double softenUpwardMotion(Entity entity, double velocity) {
        if (!(entity instanceof Player p) || p.getAbilities().flying || !inverted(entity) || velocity<=0) return velocity;
        double remaining=upwardLimit(entity)-entity.getY();
        if(remaining>=48)return velocity;
        if(remaining<=0)return 0;
        double t=Math.clamp(remaining/48,0,1);
        double fade=t*t*(3-2*t);
        return Math.min(velocity*fade,remaining*.25);
    }

    /** Searches loaded island columns for collision-free footing on their underside. */
    public static final class EndGravityTeleport {
        private EndGravityTeleport() {}
        public static boolean seekUnderside(Enderman mob) {
            if(toUnderside(mob,mob.getX(),mob.getZ()))return true;
            for(int i=0;i<8;i++) {
                double x=mob.getX()+mob.getRandom().nextInt(49)-24;
                double z=mob.getZ()+mob.getRandom().nextInt(49)-24;
                if(toUnderside(mob,x,z))return true;
            }
            return false;
        }
        public static boolean toUnderside(Enderman mob,double x,double z) {
            if(!(mob.level() instanceof ServerLevel level) || mob.isPassenger())return false;
            BlockPos column=BlockPos.containing(x,mob.getY(),z);
            if(!LoadedChunks.has(level,column) || !level.getWorldBorder().isWithinBounds(column))return false;
            int top=level.getHeight(Heightmap.Types.MOTION_BLOCKING,column.getX(),column.getZ());
            // Start at the island's top and find its first underside with enough
            // clearance. Reading the column never requests an absent chunk.
            var cursor=new BlockPos.MutableBlockPos(column.getX(),top,column.getZ());
            for(int y=top;y>level.getMinY()+mob.getBbHeight();y--) {
                cursor.setY(y);
                var support=level.getBlockState(cursor);
                if(!support.isFaceSturdy(level,cursor,Direction.DOWN) || support.is(BlockTags.ENDERMAN_DOES_NOT_TELEPORT_TO))continue;
                // Keep subtraction in double precision; float rounding can put the feet inside the ceiling.
                double footY=(double)y-mob.getBbHeight();
                var target=new Vec3(column.getX()+.5,footY,column.getZ()+.5);
                var box=mob.getBoundingBox().move(target.subtract(mob.position()));
                if(!level.noCollision(mob,box) || level.containsAnyLiquid(box))continue;
                var old=mob.position();
                mob.teleportTo(target.x,target.y,target.z);
                mob.setDeltaMovement(Vec3.ZERO);mob.resetFallDistance();mob.setOnGround(true);
                mob.getNavigation().stop();mob.needsSync=true;
                level.broadcastEntityEvent(mob,(byte)46);
                level.gameEvent(GameEvent.TELEPORT,old,GameEvent.Context.of(mob));
                if(!mob.isSilent()) {
                    level.playSound(null,old.x,old.y,old.z,SoundEvents.ENDERMAN_TELEPORT,mob.getSoundSource(),1,1);
                    mob.playSound(SoundEvents.ENDERMAN_TELEPORT,1,1);
                }
                return true;
            }
            return false;
        }
    }

    /** Temporary ground navigation while inverted; vanilla goals and A* remain in charge. */
    public static final class CeilingPathNavigation extends GroundPathNavigation {
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
        @Override protected @org.jspecify.annotations.Nullable Path createPath(Set<BlockPos> targets, int radiusOffset, boolean above, int range, float maxLength) {
            var projected = new java.util.HashSet<BlockPos>();
            for (var target : targets) {
                var surface = surfaceAt(mob, target, 4);
                projected.add(surface == null ? target : BlockPos.containing(surface.position(mob).add(0, 1e-5, 0)));
            }
            return super.createPath(projected, radiusOffset, above, range, maxLength);
        }
        @Override public @org.jspecify.annotations.Nullable Path createPath(BlockPos pos, int range) {
            // Skip GroundPathNavigation's search down to the island floor.
            return createPath(Set.of(pos), 8, false, range);
        }
        @Override public @org.jspecify.annotations.Nullable Path createPath(Entity target, int range) { return createPath(target.blockPosition(), range); }
        @Override public boolean moveTo(@org.jspecify.annotations.Nullable Path path, double speed) {
            if (path != null && path.getNodeCount() > 0 && !(path.getNode(0) instanceof CeilingNodeEvaluator.CeilingNode))
                path = createPath(path.getTarget(), 1);
            return super.moveTo(path, speed);
        }
        @Override protected Vec3 getTempMobPos() { return mob.position(); }
        @Override protected void trimPath() {} // Upright cauldron/sun trimming is inappropriate below a ceiling.
        @Override protected double getGroundY(Vec3 target) {
            var current = path;
            if (current == null || current.isDone() || !(current.getNextNode() instanceof CeilingNodeEvaluator.CeilingNode node)) return target.y;
            return node.surface.position(mob).y;
        }
        @Override public void tick() {
            tick++;
            if (hasDelayedRecomputation) recomputePath();
            if (isDone()) return;
            followThePath();
            var current = path;
            if (current != null && !current.isDone() && current.getNextNode() instanceof CeilingNodeEvaluator.CeilingNode node) {
                var target = node.surface.position(mob);
                mob.getMoveControl().setWantedPosition(target.x, target.y, target.z, speedModifier);
            }
        }
        @Override protected void followThePath() {
            var current = path;
            if (current == null || current.isDone()) return;
            if (!(current.getNextNode() instanceof CeilingNodeEvaluator.CeilingNode node)) { stop(); return; }
            var target = node.surface.position(mob);
            maxDistanceToWaypoint = Math.min(.35F, mob.getBbWidth() * .5F);
            if (Math.abs(mob.getX() - target.x) < maxDistanceToWaypoint
                    && Math.abs(mob.getZ() - target.z) < maxDistanceToWaypoint && Math.abs(mob.getY() - target.y) < .5)
                current.advance();
            doStuckDetection(mob.position());
        }
        public static @org.jspecify.annotations.Nullable CeilingSurface surfaceAt(Mob mob, BlockPos pos, int radius) {
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

    public static final class CeilingNodeEvaluator extends NodeEvaluator {
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
            return (CeilingNode) nodes.computeIfAbsent(Node.createHash(x, y, z), _ -> new CeilingNode(x, y, z, surface));
        }
        // Native PathFinder explicitly accepts null when no starting footing exists.
        @SuppressWarnings("null") @Override public @org.jspecify.annotations.Nullable Node getStart() {
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

    /** Loaded, collision-checked footing beneath a ceiling; Y is the actual foot surface. */
    public record CeilingSurface(double x, double feet, double z, float cost) {
        public Vec3 position(Mob mob) { return new Vec3(x, feet - mob.getBbHeight(), z); }
        public AABB box(Mob mob) {
            double half = mob.getBbWidth() / 2.0;
            return new AABB(x - half, feet - mob.getBbHeight(), z - half, x + half, feet, z + half).deflate(1e-5);
        }
        public static @org.jspecify.annotations.Nullable CeilingSurface find(CollisionGetter level, Mob mob, double x, double z,
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

    public interface GravityMovementAccess { void naturality$poseShift(double shift); }

    public interface GravityPoseAccess { void naturality$updateGravityPose(); }
}
