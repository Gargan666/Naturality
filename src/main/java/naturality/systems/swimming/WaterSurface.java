package naturality.systems.swimming;

import naturality.api.worlds.LoadedChunks;
import naturality.systems.rotation.PlayerRotation;
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
        private int riptideUntil;
        private int bubbleUntil;
        public Vec3 previousLean = Vec3.ZERO;
        public Vec3 lean = Vec3.ZERO;
        public float previousStroke;
        public float stroke;
    }
    public static State state(LivingEntity entity) { return ((Access)entity).naturality$waterSurface(); }
    public static boolean riptiding(LivingEntity entity) {
        return entity.isAutoSpinAttack() || entity.tickCount < state(entity).riptideUntil;
    }

    private static boolean eligible(LivingEntity entity) {
        if (entity.tickCount < state(entity).bubbleUntil) return false;
        if (entity.tickCount < state(entity).riptideUntil) return false;
        // Preserve sinking for the zombie family; drowned retain their swimming behavior.
        if (entity instanceof Zombie && !(entity instanceof Drowned)) return false;
        if (entity.isAutoSpinAttack() || !entity.isAlive() || !entity.isInWater() || entity.isPassenger() || entity.isSleeping()
                || entity.isFallFlying() || !entity.isAffectedByFluids() || entity.is(EntityTypeTags.AQUATIC)) return false;
        return !(entity instanceof Player player) || !player.isSpectator() && !player.getAbilities().flying;
    }

    /** Air above the terrain collider ends swimming; solid wall/ceiling contacts do not. */
    public static boolean fullySubmerged(Player player) {
        var body = PlayerRotation.body(player);
        var envelope = PlayerRotation.envelope(body).bounds();
        var box = body.compactTerrain ? PlayerRotation.terrain(body).bounds() : envelope;
        // Keep the surface threshold at the body's top, but sample above the occupied
        // torso footprint rather than the empty corners of a rotating enclosing box.
        box = new net.minecraft.world.phys.AABB(box.minX, box.minY, box.minZ, box.maxX, envelope.maxY, box.maxZ).deflate(1.0e-4);
        for (double x : new double[]{box.minX, box.maxX}) for (double z : new double[]{box.minZ, box.maxZ}) {
            var point = new Vec3(x, box.maxY + 0.02, z);
            var pos = BlockPos.containing(point);
            if (!LoadedChunks.has(player.level(), pos)) return false;
            var fluid = player.level().getFluidState(pos);
            if (fluid.is(FluidTags.WATER)) {
                if (pos.getY() + fluid.getHeight(player.level(), pos) >= point.y) continue;
                // Source water's fractional top under ice is not an exposed surface to climb out of.
                if (fluid.isSource() && LoadedChunks.has(player.level(), pos.above())
                        && solidAt(player, pos.above(), new Vec3(x, pos.getY() + 1.001, z))) continue;
            }
            // The sampling margin can reach into a ceiling. That is obstruction, not an exit.
            if (!solidAt(player, pos, point)) return false;
        }
        return player.isInWater();
    }

    public static boolean solidAt(LivingEntity entity, BlockPos pos, Vec3 point) {
        var shape = entity.level().getBlockState(pos).getCollisionShape(entity.level(), pos,
            net.minecraft.world.phys.shapes.CollisionContext.of(entity));
        var local = point.subtract(pos.getX(), pos.getY(), pos.getZ());
        for (var box : shape.toAabbs()) if (box.contains(local)) return true;
        return false;
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
            if (solidAt(entity, pos.above(), new Vec3(entity.getX(), y + 1.001, entity.getZ()))) continue;
            double height = y + fluid.getHeight(entity.level(), pos);
            if (height - eye < -0.6 || height - eye > 0.65) continue;
            return height;
        }
        return Double.NaN;
    }

    public static void update(LivingEntity entity) {
        var state = state(entity);
        // Detect contact before travel as well as in vanilla's later inside-block callbacks.
        var area = entity instanceof Player player && PlayerSwimming.compactTerrain(player)
            ? PlayerRotation.terrain(PlayerRotation.body(player)).bounds() : entity.getBoundingBox();
        for (var pos : BlockPos.betweenClosed(BlockPos.containing(area.minX, area.minY, area.minZ),
                BlockPos.containing(area.maxX, area.maxY, area.maxZ))) {
            if (LoadedChunks.has(entity.level(), pos)
                    && entity.level().getBlockState(pos).is(net.minecraft.world.level.block.Blocks.BUBBLE_COLUMN)) {
                bubbleContact(entity);
                break;
            }
        }
        if (entity.isAutoSpinAttack()) {
            state.surface = state.bank = Double.NaN;
            state.previousLean = state.lean = Vec3.ZERO;
            state.previousStroke = state.stroke = 0;
            state.initialized = false;
            return;
        }
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

    /** Keep the surface controller released through the launch, including the first ticks above water. */
    public static void bubbleContact(LivingEntity entity) {
        var state = state(entity);
        state.bubbleUntil = entity.tickCount + 20;
        state.surface = state.bank = Double.NaN;
    }

    public static boolean diving(Player player) {
        var input = PlayerSwimming.state(player).input;
        if (input.shift()) return true;
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
    public static boolean climbingOut(LivingEntity entity) {
        return eligible(entity) && Double.isFinite(state(entity).bank) && !(entity instanceof Player player && diving(player));
    }

    /** Local launch precedes the synchronized spin flag; release the surface on that same frame. */
    public static void releaseForRiptide(LivingEntity entity, int duration) {
        var state = state(entity);
        state.riptideUntil = entity.tickCount + Math.max(0, duration);
        state.surface = state.bank = Double.NaN;
        state.previousLean = state.lean = Vec3.ZERO;
        state.previousStroke = state.stroke = 0;
        state.initialized = false;
    }
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
