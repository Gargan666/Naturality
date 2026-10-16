package naturality.systems.swimming;

import naturality.api.worlds.LoadedChunks;
import naturality.systems.rotation.PlayerRotation;
import naturality.systems.rotation.PlayerRotation.Angles;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.joml.Quaterniond;

/** Player water locomotion policy; the rotation API remains responsible for physical clearance. */
@org.jspecify.annotations.NullMarked
public final class PlayerSwimming {
    private PlayerSwimming() { }
    public static final int CHARGE_TICKS = 20;
    public static final int COOLDOWN_TICKS = 30;
    private static final double TERRAIN_REACH = 0.65;

    public interface Access {
        State naturality$swimming();
        float naturality$swimMeter();
        void naturality$swimMeter(float value);
    }
    public static final class State {
        public Input input = Input.EMPTY;
        public boolean ownsRotation;
        private boolean inWater;
        private boolean moved;
        private float gentleStroke;
        private float previousGentleStroke;
        private final StrokeClock strokes = new StrokeClock();
        private final Dash dash = new Dash();
        private final Transition sprint = new Transition();
        private final Transition surface = new Transition();
    }
    /** Critically damped blend with continuous velocity when its target reverses. */
    public static final class Transition {
        private double previous;
        private double value;
        private double velocity;
        public void tick(boolean enabled) {
            previous = value;
            double target = enabled ? 1 : 0;
            double offset = value - target, change = velocity + 0.5 * offset;
            double decay = Math.exp(-0.5);
            value = target + (offset + change) * decay;
            velocity = (velocity - 0.5 * change) * decay;
            if (value < 0 || value > 1 || Math.abs(value - target) + Math.abs(velocity) < 0.0001) {
                value = Math.clamp(value, 0, 1);
                if (Math.abs(value - target) < 0.0001) value = target;
                velocity = 0;
            }
        }
        public float sample(float partial) { return (float)(previous + (value - previous) * Math.clamp(partial, 0, 1)); }
    }
    /** A time-driven stroke keeps moving at rest and changes pace without resetting its phase. */
    public static final class StrokeClock {
        private double previous;
        private double ticks;
        public void tick(boolean moving) { tick(moving, false); }
        public void tick(boolean moving, boolean reverse) {
            previous = ticks;
            ticks += moving ? reverse ? -1 : 1 : 0.5;
        }
        public float phase(float partial) {
            double cycle = (previous + (ticks - previous) * partial) % 32;
            return (float)((cycle < 0 ? cycle + 32 : cycle) * (Math.PI * 2 / 32));
        }
    }
    /** Charge/release state is independent of the world; callers must recheck terrain every tick. */
    public static final class Dash {
        private boolean jumpWasDown;
        private int charge;
        private int cooldown;

        public double tick(boolean ready, boolean jump) {
            if (cooldown > 0) cooldown--;
            double released = 0;
            if (cooldown == 0 && ready) {
                if (jump) charge = Math.min(CHARGE_TICKS, charge + 1);
                else if (jumpWasDown && charge > 0) {
                    released = (double)charge / CHARGE_TICKS;
                    cooldown = COOLDOWN_TICKS;
                    charge = 0;
                }
            } else charge = 0;
            jumpWasDown = jump;
            return released;
        }
        public float meter(boolean ready) { return cooldown > 0 ? -2 : ready ? (float)charge / CHARGE_TICKS : -1; }
    }
    public static State state(Player player) { return ((Access)player).naturality$swimming(); }
    /** Oblique collision slides change several axes; vanilla's per-axis cancellation loses the tangent. */
    public static boolean retainSlideVelocity(Player player, Vec3 movement) {
        if (!active(player) || !PlayerRotation.active(player)) return false;
        player.setDeltaMovement(movement);
        return true;
    }

    public static boolean active(Player player) {
        return state(player).inWater && eligible(player);
    }
    private static boolean eligible(Player player) {
        return player.isAlive() && player.isInWater() && !player.isPassenger() && !player.isSleeping()
            && !player.isSpectator() && !player.getAbilities().flying && !player.isFallFlying();
    }
    public static boolean fullSize(Player player) {
        return (state(player).ownsRotation || WaterSurface.standing(player)) && player.isAlive() && !player.isPassenger() && !player.isSleeping();
    }
    public static boolean waterMovement(Player player) { return active(player) || WaterSurface.active(player); }
    public static float meter(Player player) { return ((Access)player).naturality$swimMeter(); }
    public static float gentleStroke(Player player, float partial) {
        var state = state(player);
        return state.previousGentleStroke + (state.gentleStroke - state.previousGentleStroke) * partial;
    }
    public static float strokePhase(Player player, float partial) { return state(player).strokes.phase(partial); }
    public static float surfaceBlend(Player player, float partial) { return state(player).surface.sample(partial); }

    /** Runs after vanilla has refreshed fluid contact, on both sides. No custom input packet is needed. */
    public static void updateMode(Player player) {
        var state = state(player);
        boolean swimming = eligible(player) && WaterSurface.fullySubmerged(player);
        if (swimming && !state.ownsRotation && player.getPose() != Pose.STANDING) {
            // Never expand a crawling player into a tunnel ceiling when water first touches them.
            var standing = PlayerRotation.body(player, player.position(), player.getDimensions(Pose.STANDING), PlayerRotation.angles(player));
            swimming = PlayerRotation.fits(player, standing);
        }
        if (swimming && !state.inWater) {
            state.moved = false;
            state.ownsRotation = true;
        }
        state.inWater = swimming;
        player.setSwimming(swimming);
        if (swimming) player.setShiftKeyDown(false);
        if (player instanceof ServerPlayer serverPlayer) state.input = serverPlayer.getLastClientInput();
    }

    /** Only the server changes orientation and releases dashes; entity data supplies the client's HUD. */
    public static void tick(Player player) {
        var state = state(player);
        state.previousGentleStroke = state.gentleStroke;
        boolean moving = moving(state.input) || player.getKnownMovement().lengthSqr() > 0.0025;
        state.sprint.tick(waterMovement(player) && player.isSprinting() && moving);
        state.surface.tick(WaterSurface.active(player));
        boolean reverse = state.input.backward() && !state.input.forward();
        if (!state.input.forward() && !state.input.backward())
            reverse = player.getKnownMovement().dot(player.getLookAngle()) < -0.001;
        if (waterMovement(player)) state.strokes.tick(moving, reverse);
        float strokeTarget = waterMovement(player) ? 1 - (active(player) ? state.sprint.sample(1) : 0) : 0;
        state.gentleStroke += (strokeTarget - state.gentleStroke) * 0.18F;
        if (player.level().isClientSide()) {
            if (!active(player) && !PlayerRotation.active(player)) state.ownsRotation = false;
            return;
        }
        if (player instanceof ServerPlayer && state.ownsRotation && !player.isPassenger() && !player.isSleeping()) {
            // The connection restores this tick-start position after doTick. Test rotations at
            // that same anchor, not at the temporary position produced by server-side travel.
            player.setPos(player.xo, player.yo, player.zo);
        }
        if (!active(player)) {
            state.dash.tick(false, false);
            ((Access)player).naturality$swimMeter(-1);
            if (state.ownsRotation && (player.isPassenger() || player.isSleeping() || !player.isAlive())) {
                // Attachment/death poses supply their own position and dimensions; release swimming's transform.
                ((PlayerRotation.Access)player).naturality$rotation(Angles.ZERO);
                state.ownsRotation = false;
            }
            if (state.ownsRotation && !player.isPassenger() && !player.isSleeping()) {
                resetUpright(player);
                if (!PlayerRotation.active(player)) state.ownsRotation = false;
            }
            return;
        }
        moving = moving(state.input);
        state.moved |= moving;
        boolean surface = atSurface(player);
        // Approaching a bank restores the upright shape while vanilla's fluid step assist lifts it.
        boolean climbingOut = surface && player.horizontalCollision && moving;
        Vec3 input = new Vec3((state.input.left() ? 1 : 0) - (state.input.right() ? 1 : 0), 0,
            (state.input.forward() ? 1 : 0) - (state.input.backward() ? 1 : 0));
        Angles walk = walkAngles(swimInput(input, player.getLookAngle(), player.getYRot()), player.getYRot(), state.moved);
        Angles sprint = targetAngles(player.getLookAngle(), player.getYRot(), true, surface, moving, state.moved);
        Angles target = climbingOut ? Angles.ZERO : walk.towards(sprint, state.sprint.sample(1));
        var pressure = moving ? swimInput(input, player.getLookAngle(), player.getYRot()).scale(0.2) : player.getKnownMovement();
        var contact = PlayerRotation.deflect(player, pressure, Math.toRadians(6));
        syncDisplacement(player, contact.displacement());
        // Holding against terrain must not fight the contact response with the view-driven pose.
        if (!contact.blocked()) turn(player, target);

        boolean ready = player.isEyeInFluid(FluidTags.WATER) && nearTerrain(player);
        double released = state.dash.tick(ready, state.input.jump());
        if (released > 0) {
            player.setDeltaMovement(player.getLookAngle().scale(0.4 + 0.8 * released));
            // Ordinary tracker movement excludes the controlling player; this flag also sends to self.
            player.syncVelocity = true;
            player.causeFoodExhaustion(0.2F);
        }
        ((Access)player).naturality$swimMeter(state.dash.meter(ready));
    }

    private static void turn(Player player, Angles target) {
        var previous = PlayerRotation.angles(player);
        if (previous.equals(target)) return;
        double distance = previous.radiansTo(target);
        double fraction = distance < Math.toRadians(0.05) ? 1 : Math.min(0.18, Math.toRadians(6) / distance);
        // Free a corner resting on the bottom, but never climb a wall just to chase a blocked turn.
        double lift = PlayerRotation.rotateWithLift(player, previous.towards(target, fraction), 0.12, false);
        syncLift(player, lift);
    }

    private static void resetUpright(Player player) {
        var previous = PlayerRotation.angles(player);
        if (!previous.active()) return;
        double distance = previous.radiansTo(Angles.ZERO);
        double fraction = distance < Math.toRadians(0.25) ? 1 : Math.min(0.4, Math.toRadians(12) / distance);
        double lift = PlayerRotation.rotateWithLift(player, previous.towards(Angles.ZERO, fraction), 0.25);
        syncLift(player, lift);
    }

    private static void syncLift(Player player, double lift) {
        syncDisplacement(player, new Vec3(0, lift, 0));
    }

    private static void syncDisplacement(Player player, Vec3 displacement) {
        if (displacement.lengthSqr() > PlayerRotation.EPSILON * PlayerRotation.EPSILON && player instanceof ServerPlayer serverPlayer) {
            // Keep view input and axes not moved by the correction relative to the client.
            var relatives = java.util.EnumSet.of(Relative.Y_ROT, Relative.X_ROT, Relative.DELTA_X, Relative.DELTA_Y, Relative.DELTA_Z);
            if (displacement.x == 0) relatives.add(Relative.X);
            if (displacement.y == 0) relatives.add(Relative.Y);
            if (displacement.z == 0) relatives.add(Relative.Z);
            var position = new Vec3(displacement.x == 0 ? 0 : player.getX(), displacement.y == 0 ? 0 : player.getY(), displacement.z == 0 ? 0 : player.getZ());
            serverPlayer.connection.teleport(new PositionMoveRotation(position, Vec3.ZERO, 0, 0), relatives);
            // The connection restores its tick-start position after Player.tick. Preserve this
            // authoritative lift there too, or the new orientation spends a tick inside the floor.
            serverPlayer.connection.resetPosition();
        }
    }

    public static Angles walkAngles(Vec3 movement, float yaw, boolean hasMoved) {
        if (!hasMoved) return Angles.ZERO;
        double length = movement.horizontalDistance();
        if (length < 1.0e-6) {
            if (movement.lengthSqr() > 1.0e-6) return Angles.ZERO;
            double heading = Math.toRadians(yaw);
            return Angles.fromQuaternion(new Quaterniond().rotationAxis(Math.toRadians(10), Math.cos(heading), 0, Math.sin(heading)));
        }
        double tilt = Math.toRadians(20) * Math.min(1, length);
        return Angles.fromQuaternion(new Quaterniond().rotationAxis(tilt, movement.z / length, 0, -movement.x / length));
    }

    public static boolean moving(Input input) {
        return input.forward() != input.backward() || input.left() != input.right();
    }

    /** A pure tilt about the horizontal right axis. Matching only the head direction leaves unwanted roll. */
    public static Angles targetAngles(Vec3 look, float yaw, boolean sprinting, boolean surface, boolean moving,
                                     boolean hasMoved) {
        if (!hasMoved) return Angles.ZERO;
        if (!sprinting) return walkAngles(moving ? look : Vec3.ZERO, yaw, hasMoved);
        double tilt = moving ? surface ? Math.toRadians(80)
            : Math.acos(Math.clamp(look.y, -1, 1)) : Math.toRadians(10);
        // Use view yaw explicitly: the horizontal components of a vertical look vector are rounding
        // noise and can reverse sign at +/-90 degrees, flipping the torso's tilt axis by 180 degrees.
        double heading = Math.toRadians(yaw);
        return Angles.fromQuaternion(new Quaterniond().rotationAxis(tilt, Math.cos(heading), 0, Math.sin(heading)));
    }

    public static Vec3 swimInput(Vec3 input, Vec3 look, float yaw) {
        double radians = Math.toRadians(yaw);
        Vec3 side = new Vec3(Math.cos(radians), 0, Math.sin(radians));
        Vec3 intent = look.scale(input.z).add(side.scale(input.x));
        return intent.lengthSqr() > 1 ? intent.normalize() : intent;
    }

    /** Called in place of Player.travel only while in water. Vanilla collision and block effects still run. */
    public static void travel(Player player, Vec3 input) {
        if (!player.isEffectiveAi()) return;
        double oldY = player.getY();
        Vec3 intent = swimInput(input, player.getLookAngle(), player.getYRot());
        double efficiency = Math.clamp(player.getAttributeValue(Attributes.WATER_MOVEMENT_EFFICIENCY), 0, 1);
        double acceleration = (0.025 + 0.02 * state(player).sprint.sample(1)) * (1 + efficiency);
        double drag = player.hasEffect(MobEffects.DOLPHINS_GRACE) ? 0.96 : 0.85;
        Vec3 velocity = player.getDeltaMovement().add(intent.scale(acceleration));
        // Jump still allows ordinary ascent away from terrain; near terrain it exclusively charges a dash.
        if (active(player) && state(player).input.jump() && !(player.isEyeInFluid(FluidTags.WATER) && nearTerrain(player))) {
            velocity = velocity.add(0, 0.03, 0);
        }
        player.setDeltaMovement(velocity);
        WaterSurface.buoyancy(player);
        player.move(MoverType.SELF, player.getDeltaMovement());
        player.setDeltaMovement(player.getDeltaMovement().scale(drag));
        // Same clearance test and impulse as LivingEntity.jumpOutOfFluid, only near the water surface.
        Vec3 remaining = player.getDeltaMovement();
        if (atSurface(player) && player.horizontalCollision
                && player.isFree(remaining.x, remaining.y + 0.6 - player.getY() + oldY, remaining.z)) {
            player.setDeltaMovement(remaining.x, 0.3, remaining.z);
        }
    }

    /** Surface policy only; general entity flotation is deliberately owned by the later water feature. */
    public static boolean atSurface(Player player) {
        if (!player.isEyeInFluid(FluidTags.WATER)) return true;
        Vec3 eye = player.getEyePosition();
        var above = BlockPos.containing(eye.add(0, 0.3, 0));
        return LoadedChunks.has(player.level(), above) && !player.level().getFluidState(above).is(FluidTags.WATER)
            && !(moving(state(player).input) && player.getLookAngle().y < -0.25);
    }

    /** Search actual block shapes around the oriented body, excluding entities and fluids. */
    public static boolean nearTerrain(Player player) {
        var body = PlayerRotation.body(player);
        var search = body.bounds().inflate(TERRAIN_REACH);
        if (search.getXsize() * search.getYsize() * search.getZsize() > 512) return false;
        var context = CollisionContext.of(player);
        for (var pos : BlockPos.betweenClosed(BlockPos.containing(search.minX, search.minY, search.minZ),
                BlockPos.containing(search.maxX, search.maxY, search.maxZ))) {
            if (!LoadedChunks.has(player.level(), pos)) continue;
            var block = player.level().getBlockState(pos);
            for (var box : block.getCollisionShape(player.level(), pos, context).toAabbs()) {
                if (body.separation(box.move(pos)) <= TERRAIN_REACH) return true;
            }
        }
        return false;
    }
}
