package naturality.systems.swimming;

import naturality.api.worlds.LoadedChunks;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.monster.zombie.Drowned;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** Surface buoyancy uses ordinary movement/collision; its lean is presentation only. */
@org.jspecify.annotations.NullMarked
public final class WaterSurface {
    public static final double EYE_CLEARANCE = 0.12;
    private WaterSurface() { }

    public interface Access { State naturality$waterSurface(); }
    public static final class State {
        private double surface = Double.NaN;
        private double bank = Double.NaN;
        private Vec3 previousPosition = Vec3.ZERO;
        private boolean initialized;
        public Vec3 previousLean = Vec3.ZERO;
        public Vec3 lean = Vec3.ZERO;
        public float previousStroke;
        public float stroke;
    }
    public static State state(LivingEntity entity) { return ((Access)entity).naturality$waterSurface(); }

    private static boolean eligible(LivingEntity entity) {
        // Preserve sinking for the zombie family; drowned retain their swimming behavior.
        if (entity instanceof Zombie && !(entity instanceof Drowned)) return false;
        if (!entity.isAlive() || !entity.isInWater() || entity.isPassenger() || entity.isSleeping()
                || entity.isFallFlying() || !entity.isAffectedByFluids() || entity.is(EntityTypeTags.AQUATIC)) return false;
        return !(entity instanceof Player player) || !player.isSpectator() && !player.getAbilities().flying;
    }

    /** Fluid at the upper face must cover the entire physical box, not merely the eyes. */
    public static boolean fullySubmerged(Player player) {
        var box = player.getBoundingBox().deflate(1.0e-4);
        for (double x : new double[]{box.minX, box.maxX}) for (double z : new double[]{box.minZ, box.maxZ}) {
            var pos = BlockPos.containing(x, box.maxY + 0.02, z);
            if (!LoadedChunks.has(player.level(), pos)) return false;
            var fluid = player.level().getFluidState(pos);
            if (!fluid.is(FluidTags.WATER) || pos.getY() + fluid.getHeight(player.level(), pos) < box.maxY + 0.02) return false;
        }
        return player.isInWater();
    }

    /** Bounded, loaded-only search near the head; deep swimmers are never hauled to the surface. */
    public static double surface(LivingEntity entity) {
        if (!eligible(entity)) return Double.NaN;
        double eye = entity.getEyeY();
        if (entity instanceof Player player) eye = player.getEyePosition().y;
        var column = BlockPos.containing(entity.getX(), eye, entity.getZ());
        for (int y = (int)Math.floor(eye + 0.6); y >= (int)Math.floor(eye - 0.75); y--) {
            var pos = new BlockPos(column.getX(), y, column.getZ());
            if (!LoadedChunks.has(entity.level(), pos) || !LoadedChunks.has(entity.level(), pos.above())) return Double.NaN;
            var fluid = entity.level().getFluidState(pos);
            if (!fluid.is(FluidTags.WATER) || entity.level().getFluidState(pos.above()).is(FluidTags.WATER)) continue;
            double height = y + fluid.getHeight(entity.level(), pos);
            if (height - eye < -0.6 || height - eye > 0.65) continue;
            return height;
        }
        return Double.NaN;
    }

    public static void update(LivingEntity entity) {
        var state = state(entity);
        if (entity instanceof Player player && fullySubmerged(player)) {
            state.surface = state.bank = Double.NaN;
            return;
        }
        double captured = surface(entity);
        double height = captured;
        // While climbing, the eyes leave the capture band before the feet reach the bank.
        if (!Double.isFinite(height) && Double.isFinite(state.bank) && eligible(entity)) height = state.surface;
        // A shallow ledge supports walking even while the legs are still in water.
        if (entity instanceof Player player && player.onGround() && Double.isFinite(height)
                && player.getEyeY() >= height + EYE_CLEARANCE - 0.02) {
            state.surface = state.bank = Double.NaN;
            return;
        }
        state.bank = Double.isFinite(height) ? bank(entity, height) : Double.NaN;
        state.surface = Double.isFinite(state.bank) ? height : captured;
    }
    public static boolean active(LivingEntity entity) { return eligible(entity) && Double.isFinite(state(entity).surface); }

    public static boolean diving(Player player) {
        var input = PlayerSwimming.state(player).input;
        double forward = (input.forward() ? 1 : 0) - (input.backward() ? 1 : 0);
        return forward * player.getLookAngle().y < -0.25;
    }

    /** Find a nearby low bank with full standing clearance, without treating tall walls as exits. */
    private static double bank(LivingEntity entity, double surface) {
        Vec3 direction = entity.getDeltaMovement().multiply(1, 0, 1);
        if (entity instanceof Player player) {
            if (diving(player)) return Double.NaN;
            var input = PlayerSwimming.state(player).input;
            direction = PlayerSwimming.swimInput(new Vec3((input.left() ? 1 : 0) - (input.right() ? 1 : 0), 0,
                (input.forward() ? 1 : 0) - (input.backward() ? 1 : 0)), player.getLookAngle(), player.getYRot()).multiply(1, 0, 1);
        } else if (entity.horizontalCollision && direction.horizontalDistanceSqr() < 1.0e-6) {
            direction = entity.getLookAngle().multiply(1, 0, 1);
        }
        if (direction.horizontalDistanceSqr() < 1.0e-6) return Double.NaN;
        direction = direction.normalize().scale(entity.getBbWidth() / 2 + 0.55);
        var ahead = entity.position().add(direction);
        var context = net.minecraft.world.phys.shapes.CollisionContext.of(entity);
        double minimumTop = entity instanceof Player ? surface + EYE_CLEARANCE - entity.getEyeHeight() : surface - 0.2;
        for (int y = (int)Math.floor(surface + 0.6); y >= (int)Math.floor(minimumTop); y--) {
            var pos = BlockPos.containing(ahead.x, y, ahead.z);
            if (!LoadedChunks.has(entity.level(), pos)) return Double.NaN;
            for (var box : entity.level().getBlockState(pos).getCollisionShape(entity.level(), pos, context).toAabbs()) {
                double top = y + box.maxY;
                if (top < minimumTop || top > surface + 0.6 || entity.getY() >= top - 0.02) continue;
                var standing = entity instanceof Player player
                    ? player.getDimensions(Pose.STANDING).makeBoundingBox(ahead.x, top + 0.001, ahead.z)
                    : entity.getBoundingBox().move(direction.x, top + 0.001 - entity.getY(), direction.z);
                if (!LoadedChunks.has(entity.level(), BlockPos.containing(standing.minX, standing.minY, standing.minZ))
                        || !LoadedChunks.has(entity.level(), BlockPos.containing(standing.maxX, standing.maxY, standing.maxZ))) continue;
                if (entity.level().noCollision(entity, standing)) return top;
            }
        }
        return Double.NaN;
    }
    public static boolean climbingOut(LivingEntity entity) { return Double.isFinite(state(entity).bank); }
    public static boolean controlsHeight(LivingEntity entity) {
        return active(entity) && !climbingOut(entity) && !(entity instanceof Player player && diving(player));
    }

    public static double verticalSpeed(double error) { return Math.clamp(error * 0.3, -0.12, 0.12); }

    /** Called before ordinary water travel, so the vertical correction goes through block collision. */
    public static void buoyancy(LivingEntity entity) {
        if (!active(entity) || !entity.isEffectiveAi()) return;
        if (climbingOut(entity)) {
            // Bring hanging feet within vanilla's step-out reach, through normal swept movement.
            var velocity = entity.getDeltaMovement();
            entity.setDeltaMovement(velocity.x, Math.max(velocity.y, 0.12), velocity.z);
            return;
        }
        if (!controlsHeight(entity)) return;
        double eye = entity instanceof Player player ? player.getEyePosition().y : entity.getEyeY();
        var velocity = entity.getDeltaMovement();
        entity.setDeltaMovement(velocity.x, verticalSpeed(state(entity).surface + EYE_CLEARANCE - eye), velocity.z);
        entity.resetFallDistance();
    }

    public static Vec3 lean(Vec3 movement) {
        double speed = movement.horizontalDistance();
        if (speed < 1.0e-5) return Vec3.ZERO;
        double radians = Math.toRadians(20) * Math.min(1, speed / 0.1);
        return new Vec3(movement.z / speed * radians, 0, -movement.x / speed * radians);
    }

    public static void tick(LivingEntity entity) {
        var state = state(entity);
        state.previousLean = state.lean;
        state.previousStroke = state.stroke;
        Vec3 motion = state.initialized ? entity.position().subtract(state.previousPosition) : Vec3.ZERO;
        state.previousPosition = entity.position();
        state.initialized = true;
        boolean visible = active(entity) && !climbingOut(entity);
        Vec3 target = visible ? lean(motion) : Vec3.ZERO;
        state.lean = state.lean.lerp(target, 0.2);
        float stroke = visible ? (float)Math.min(1, motion.horizontalDistance() / 0.06) : 0;
        state.stroke += (stroke - state.stroke) * 0.2F;
        if (!eligible(entity)) state.surface = Double.NaN;
    }

    public static boolean standing(Player player) {
        return active(player) && player.getPose() != Pose.SLEEPING;
    }
}
