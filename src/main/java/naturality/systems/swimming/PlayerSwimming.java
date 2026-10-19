package naturality.systems.swimming;

import naturality.api.worlds.LoadedChunks;
import naturality.systems.rotation.PlayerRotation;
import naturality.systems.rotation.PlayerRotation.Angles;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;
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
        boolean naturality$compactTerrain();
        void naturality$compactTerrain(boolean value);
        boolean naturality$braced();
        void naturality$braced(boolean value);
        float naturality$swimMeter();
        void naturality$swimMeter(float value);
    }
    public static final class State {
        public Input input = Input.EMPTY;
        public boolean ownsRotation;
        private boolean inWater;
        private boolean moved;
        private boolean resumeFromSpin;
        private @Nullable Anchor anchor;
        private boolean chargeHeld;
        private final Transition brace = new Transition();
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
        private double rate = 0.5;
        private float amplitude = 0.7F;
        private float previousAmplitude = 0.7F;
        public void tick(boolean moving) { tick(moving, false); }
        public void tick(boolean moving, boolean reverse) {
            advance(moving ? reverse ? -1 : 1 : 0.5);
        }
        public void tick(double speed, boolean reverse) {
            double target = Math.clamp(Math.sqrt(Math.max(0, speed) / 0.1), 0.5, 2);
            if (reverse && speed > 0.001) target = -target;
            double oldRate = rate;
            rate += (target - rate) * 0.2;
            previousAmplitude = amplitude;
            amplitude += ((float)Math.clamp(0.7 + speed * 3, 0.7, 1.5) - amplitude) * 0.2F;
            advance((oldRate + rate) * 0.5);
        }
        public float amplitude(float partial) { return previousAmplitude + (amplitude - previousAmplitude) * Math.clamp(partial, 0, 1); }
        private void advance(double rate) {
            previous = ticks;
            ticks += rate;
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
        return !naturality.items.heavyboots.HeavyBoots.worn(player) && !naturality.systems.swimming.WaterSurface.riptiding(player) && player.isAlive() && player.isInWater() && !player.isPassenger() && !player.isSleeping()
            && !player.isSpectator() && !player.getAbilities().flying && !player.isFallFlying();
    }
    public static boolean fullSize(Player player) {
        if (naturality.systems.swimming.WaterSurface.riptiding(player)) return false;
        if (player.level().isClientSide() && player.getPose() == Pose.SWIMMING
                && !((Access)player).naturality$compactTerrain()) return false;
        return (state(player).ownsRotation || WaterSurface.standing(player)) && player.isAlive() && !player.isPassenger() && !player.isSleeping();
    }
    public static boolean compactTerrain(Player player) {
        return !naturality.systems.swimming.WaterSurface.riptiding(player) && (player.isSwimming() || state(player).ownsRotation);
    }
    public static boolean waterMovement(Player player) { return active(player) || WaterSurface.active(player); }
    public static float meter(Player player) { return naturality.systems.swimming.WaterSurface.riptiding(player) ? -1 : ((Access)player).naturality$swimMeter(); }
    public static boolean braced(Player player) { return !naturality.systems.swimming.WaterSurface.riptiding(player) && ((Access)player).naturality$braced(); }
    public static float braceBlend(Player player, float partial) { return naturality.systems.swimming.WaterSurface.riptiding(player) ? 0 : state(player).brace.sample(partial); }
    public static float gentleStroke(Player player, float partial) {
        if (naturality.systems.swimming.WaterSurface.riptiding(player)) return 0;
        var state = state(player);
        return state.previousGentleStroke + (state.gentleStroke - state.previousGentleStroke) * partial;
    }
    public static float strokePhase(Player player, float partial) { return state(player).strokes.phase(partial); }
    public static float strokeAmplitude(Player player, float partial) { return state(player).strokes.amplitude(partial); }
    public static float surfaceBlend(Player player, float partial) { return naturality.systems.swimming.WaterSurface.riptiding(player) ? 0 : state(player).surface.sample(partial); }

    /** Riptide owns movement and pose immediately; no queued dash or exit recovery may survive it. */
    public static void startRiptide(Player player, int ticks) {
        boolean customBox = state(player).ownsRotation;
        double oldHeight = player.getBbHeight();
        WaterSurface.releaseForRiptide(player, ticks);
        releaseForRiptide(player);
        player.setPose(Pose.SPIN_ATTACK);
        if (customBox) {
            // Vanilla's short spin pose must occupy the existing swimming cube rather
            // than dropping its centre into the seabed when the full hurtbox disappears.
            Vec3 shift = new Vec3(0, (oldHeight - player.getBbHeight()) / 2, 0);
            player.setPos(player.position().add(shift));
            syncDisplacement(player, shift);
        }
    }

    public static void releaseForRiptide(Player player) {
        var state = state(player);
        state.resumeFromSpin = true;
        boolean wasSwimming = state.inWater || state.ownsRotation;
        state.inWater = state.ownsRotation = state.moved = state.chargeHeld = false;
        state.anchor = null;
        state.dash.charge = state.dash.cooldown = 0;
        state.dash.jumpWasDown = false;
        state.gentleStroke = state.previousGentleStroke = 0;
        for (var blend : new Transition[]{state.brace, state.sprint, state.surface}) {
            blend.previous = blend.value = blend.velocity = 0;
        }
        ((Access)player).naturality$compactTerrain(false);
        ((Access)player).naturality$braced(false);
        ((Access)player).naturality$swimMeter(-1);
        ((PlayerRotation.Access)player).naturality$rotation(Angles.ZERO);
        if (wasSwimming) player.setSwimming(false);
    }

    /** Runs after vanilla has refreshed fluid contact, on both sides. No custom input packet is needed. */
    public static void updateMode(Player player) {
        var state = state(player);
        boolean swimming = eligible(player) && WaterSurface.fullySubmerged(player);
        if (swimming && !state.resumeFromSpin && !state.ownsRotation && player.getPose() != Pose.STANDING) {
            // Never expand a crawling player into a tunnel ceiling when water first touches them.
            var standing = PlayerRotation.body(player, player.position(), player.getDimensions(Pose.STANDING), PlayerRotation.angles(player));
            swimming = PlayerRotation.fits(player, standing);
        }
        if (swimming && !state.inWater) {
            state.moved = state.resumeFromSpin;
            double oldHeight = player.getBbHeight();
            state.ownsRotation = true;
            if (!player.level().isClientSide()) ((Access)player).naturality$compactTerrain(true);
            if (state.resumeFromSpin) {
                // Resume from the head-first spin, never from an intermediate upright body.
                ((PlayerRotation.Access)player).naturality$rotation(targetAngles(player.getLookAngle(), player.getYRot(), true, false, true, true));
                player.setPose(Pose.STANDING);
                player.refreshDimensions();
                Vec3 shift = new Vec3(0, (oldHeight - player.getBbHeight()) / 2, 0);
                player.setPos(player.position().add(shift));
                player.xo = player.getX(); player.yo = player.getY(); player.zo = player.getZ();
                syncDisplacement(player, shift);
            }
        }
        if (!WaterSurface.riptiding(player)) state.resumeFromSpin = false;
        state.inWater = swimming;
        player.setSwimming(swimming);
        if (swimming) player.setShiftKeyDown(false);
        if (player instanceof ServerPlayer serverPlayer) state.input = serverPlayer.getLastClientInput();
    }

    /** Only the server changes orientation and releases dashes; entity data supplies the client's HUD. */
    public static void tick(Player player) {
        if (naturality.systems.swimming.WaterSurface.riptiding(player)) { releaseForRiptide(player); return; }
        var state = state(player);
        state.previousGentleStroke = state.gentleStroke;
        state.brace.tick(braced(player));
        boolean moving = !braced(player) && (moving(state.input) || player.getKnownMovement().lengthSqr() > 0.0025);
        state.sprint.tick(waterMovement(player) && player.isSprinting() && moving);
        state.surface.tick(WaterSurface.active(player));
        boolean reverse = state.input.backward() && !state.input.forward();
        if (!state.input.forward() && !state.input.backward())
            reverse = player.getKnownMovement().dot(player.getLookAngle()) < -0.001;
        if (waterMovement(player)) state.strokes.tick(braced(player) ? 0 : player.getKnownMovement().length(), reverse);
        float strokeTarget = waterMovement(player) ? 1 - (active(player) && !braced(player) ? state.sprint.sample(1) : 0) : 0;
        state.gentleStroke += (strokeTarget - state.gentleStroke) * 0.18F;
        if (player.level().isClientSide()) {
            state.ownsRotation = active(player) || ((Access)player).naturality$compactTerrain();
            // Pose and compact-terrain metadata can arrive in either order. The pose
            // callback may have cached standing dimensions while ownership was still set.
            if (player.getPose() == Pose.SWIMMING && !((Access)player).naturality$compactTerrain()
                    && player.getBbHeight() != player.getDimensions(Pose.SWIMMING).height()) player.refreshDimensions();
            return;
        }
        if (player instanceof ServerPlayer && state.ownsRotation && !player.isPassenger() && !player.isSleeping()) {
            // The connection restores this tick-start position after doTick. Test rotations at
            // that same anchor, not at the temporary position produced by server-side travel.
            player.setPos(player.xo, player.yo, player.zo);
        }
        if (!active(player)) {
            state.anchor = null;
            state.chargeHeld = false;
            ((Access)player).naturality$braced(false);
            state.dash.tick(false, false);
            ((Access)player).naturality$swimMeter(-1);
            if (state.ownsRotation && (player.isPassenger() || player.isSleeping() || !player.isAlive())) {
                // Attachment/death poses supply their own position and dimensions; release swimming's transform.
                ((PlayerRotation.Access)player).naturality$rotation(Angles.ZERO);
                state.ownsRotation = false;
            }
            if (state.ownsRotation && !player.isPassenger() && !player.isSleeping()) {
                if (!player.isInWater() && enterTunnelCrawl(player)) return;
                resetUpright(player);
                if (!PlayerRotation.active(player)) {
                    var body = PlayerRotation.body(player);
                    // Do not expand into a bank/ceiling when the torso finishes righting itself.
                    var normal = new PlayerRotation.Box(body.center, body.half, Angles.ZERO);
                    if (PlayerRotation.fits(player, normal)) state.ownsRotation = false;
                }
            }
            ((Access)player).naturality$compactTerrain(state.ownsRotation);
            return;
        }
        if (chargeAtSurface(player)) return;
        moving = moving(state.input);
        state.moved |= moving;
        boolean surface = atSurface(player);
        // A tall wall is not a bank, even when close to the water surface.
        boolean climbingOut = WaterSurface.climbingOut(player) && moving;
        Vec3 input = new Vec3((state.input.left() ? 1 : 0) - (state.input.right() ? 1 : 0), 0,
            (state.input.forward() ? 1 : 0) - (state.input.backward() ? 1 : 0));
        Angles walk = walkAngles(swimInput(input, player.getLookAngle(), player.getYRot()), player.getYRot(), state.moved);
        Angles sprint = targetAngles(player.getLookAngle(), player.getYRot(), true, surface, moving, state.moved);
        Angles target = climbingOut ? Angles.ZERO : walk.towards(sprint, state.sprint.sample(1));
        // Keep the requested tunnel idle posture without constraining active steering.
        if (!moving && lowCeiling(player, PlayerRotation.body(player))) target = PlayerRotation.angles(player);
        turn(player, target);

        boolean ready = player.isEyeInFluid(FluidTags.WATER) && nearTerrain(player);
        ((Access)player).naturality$swimMeter(state.dash.meter(ready));
    }

    /** A transient fixed face; holding the key never selects another supporting block. */
    private record Anchor(BlockPos block, AABB shape, Vec3 feet, Vec3 normal, Angles rotation) { }

    private static boolean chargeAtSurface(Player player) {
        var state = state(player);
        boolean held = state.input.jump();
        if (held && !state.chargeHeld && state.dash.cooldown == 0 && player.isEyeInFluid(FluidTags.WATER))
            state.anchor = nearestAnchor(player);
        state.chargeHeld = held;
        var anchor = state.anchor;
        boolean valid = anchor != null && LoadedChunks.has(player.level(), anchor.block)
            && player.level().getBlockState(anchor.block).getCollisionShape(player.level(), anchor.block, CollisionContext.of(player))
                .toAabbs().stream().anyMatch(box -> box.move(anchor.block).equals(anchor.shape));
        if (!valid) state.anchor = null;
        ((Access)player).naturality$braced(valid && held);
        if (valid && held && anchor != null) {
            var body = PlayerRotation.body(player);
            double angle = PlayerRotation.angles(player).radiansTo(anchor.rotation);
            // Approach feet-first; translation uses the separate terrain cube.
            double clearance = body.half.y + 0.002;
            Vec3 goal = anchor.feet.add(anchor.normal.scale(clearance));
            Vec3 offset = goal.subtract(body.center);
            Vec3 movement = PlayerRotation.collide(player, offset.scale(Math.min(0.3, 0.1 / Math.max(0.1, offset.length()))));
            player.setPos(player.position().add(movement));
            syncDisplacement(player, movement);
            var current = PlayerRotation.angles(player);
            PlayerRotation.rotate(player, current.towards(anchor.rotation,
                angle < Math.toRadians(0.1) ? 1 : Math.min(0.25, Math.toRadians(8) / angle)));
            player.setDeltaMovement(Vec3.ZERO);
            player.syncVelocity = true;
        }
        double released = state.dash.tick(valid, held);
        if (!held) state.anchor = null;
        if (released > 0) {
            player.setDeltaMovement(player.getLookAngle().scale(0.4 + 0.8 * released));
            player.syncVelocity = true;
            player.causeFoodExhaustion(0.2F);
        }
        ((Access)player).naturality$swimMeter(state.dash.meter(valid || nearTerrain(player)));
        return valid && held;
    }

    private static @Nullable Anchor nearestAnchor(Player player) {
        var body = PlayerRotation.body(player);
        var search = body.bounds().inflate(TERRAIN_REACH);
        Anchor best = null;
        double bestDistance = Double.POSITIVE_INFINITY;
        for (var pos : BlockPos.betweenClosed(BlockPos.containing(search.minX, search.minY, search.minZ),
                BlockPos.containing(search.maxX, search.maxY, search.maxZ))) {
            if (!LoadedChunks.has(player.level(), pos)) continue;
            for (var local : player.level().getBlockState(pos).getCollisionShape(player.level(), pos, CollisionContext.of(player)).toAabbs()) {
                var box = local.move(pos);
                if (body.separation(box) > TERRAIN_REACH) continue;
                for (var direction : Direction.values()) {
                    Vec3 normal = new Vec3(direction.getStepX(), direction.getStepY(), direction.getStepZ());
                    Vec3 point = new Vec3(Math.clamp(body.center.x, box.minX, box.maxX),
                        Math.clamp(body.center.y, box.minY, box.maxY), Math.clamp(body.center.z, box.minZ, box.maxZ));
                    point = switch (direction) {
                        case DOWN -> new Vec3(point.x, box.minY, point.z);
                        case UP -> new Vec3(point.x, box.maxY, point.z);
                        case NORTH -> new Vec3(point.x, point.y, box.minZ);
                        case SOUTH -> new Vec3(point.x, point.y, box.maxZ);
                        case WEST -> new Vec3(box.minX, point.y, point.z);
                        case EAST -> new Vec3(box.maxX, point.y, point.z);
                    };
                    if (body.center.subtract(point).dot(normal) < 0) continue;
                    double distance = body.center.distanceToSqr(point);
                    if (distance >= bestDistance) continue;
                    Vec3 up = PlayerRotation.angles(player).transform(new Vec3(0, 1, 0));
                    Angles rotation = Angles.fromQuaternion(new Quaterniond().rotationTo(up.x, up.y, up.z,
                        normal.x, normal.y, normal.z).mul(PlayerRotation.angles(player).quaternion()));
                    var target = new PlayerRotation.Box(point.add(normal.scale(body.half.y + 0.002)), body.half, rotation, body.compactTerrain);
                    if (!PlayerRotation.fits(player, target)) continue;
                    best = new Anchor(pos.immutable(), box, point, normal, rotation);
                    bestDistance = distance;
                }
            }
        }
        return best;
    }

    private static void turn(Player player, Angles target) {
        var previous = PlayerRotation.angles(player);
        if (previous.equals(target)) return;
        double distance = previous.radiansTo(target);
        double fraction = distance < Math.toRadians(0.05) ? 1 : Math.min(0.18, Math.toRadians(6) / distance);
        Angles next = previous.towards(target, fraction);
        PlayerRotation.rotate(player, next);
    }

    private static boolean lowCeiling(Player player, PlayerRotation.Box body) {
        double radius = body.half.x;
        var above = new AABB(body.center.x - radius, body.center.y + radius + PlayerRotation.EPSILON,
            body.center.z - radius, body.center.x + radius,
            body.center.y - radius + player.getDimensions(Pose.STANDING).height(), body.center.z + radius);
        return player.level().getBlockCollisions(player, above.deflate(PlayerRotation.EPSILON)).iterator().hasNext();
    }

    /** Release the custom standing-sized body without moving its compact terrain footprint. */
    private static boolean enterTunnelCrawl(Player player) {
        var body = PlayerRotation.body(player);
        if (!lowCeiling(player, body)) return false;
        var cube = PlayerRotation.terrain(body);
        if (!PlayerRotation.fits(player, cube)) return false;
        Vec3 oldPosition = player.position();
        Vec3 crawlPosition = cube.center.subtract(0, cube.half.y, 0);
        state(player).ownsRotation = false;
        ((Access)player).naturality$compactTerrain(false);
        player.setSwimming(false);
        ((PlayerRotation.Access)player).naturality$rotation(Angles.ZERO);
        player.setPose(Pose.SWIMMING);
        player.setPos(crawlPosition);
        syncDisplacement(player, crawlPosition.subtract(oldPosition));
        return true;
    }

    private static void resetUpright(Player player) {
        var previous = PlayerRotation.angles(player);
        if (!previous.active()) return;
        double distance = previous.radiansTo(Angles.ZERO);
        double fraction = distance < Math.toRadians(0.25) ? 1 : Math.min(0.4, Math.toRadians(12) / distance);
        PlayerRotation.rotate(player, previous.towards(Angles.ZERO, fraction));
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
        if (braced(player) && state(player).input.jump()) {
            player.setDeltaMovement(Vec3.ZERO);
            return;
        }
        double oldY = player.getY();
        Vec3 intent = swimInput(input, player.getLookAngle(), player.getYRot());
        double efficiency = Math.clamp(player.getAttributeValue(Attributes.WATER_MOVEMENT_EFFICIENCY), 0, 1);
        double acceleration = (0.025 + 0.02 * state(player).sprint.sample(1)) * (1 + efficiency);
        double drag = player.hasEffect(MobEffects.DOLPHINS_GRACE) ? 0.96 : 0.85;
        Vec3 velocity = player.getDeltaMovement().add(intent.scale(acceleration));
        if (state(player).input.shift()) velocity = velocity.add(0, -0.03, 0);
        // Jump still allows ordinary ascent away from terrain; near terrain it exclusively charges a dash.
        if (active(player) && state(player).input.jump() && !(player.isEyeInFluid(FluidTags.WATER) && nearTerrain(player))) {
            velocity = velocity.add(0, 0.03, 0);
        }
        player.setDeltaMovement(velocity);
        WaterSurface.buoyancy(player);
        player.move(MoverType.SELF, player.getDeltaMovement());
        player.setDeltaMovement(player.getDeltaMovement().scale(drag));
        // Only a verified walkable bank may receive the vanilla fluid-exit impulse.
        Vec3 remaining = player.getDeltaMovement();
        if (WaterSurface.climbingOut(player) && player.horizontalCollision
                && player.isFree(remaining.x, remaining.y + 0.6 - player.getY() + oldY, remaining.z)) {
            player.setDeltaMovement(remaining.x, 0.3, remaining.z);
        }
    }

    /** A nearby exposed water surface, not simply any non-water block above the eyes. */
    public static boolean atSurface(Player player) {
        return Double.isFinite(WaterSurface.surface(player)) && !WaterSurface.diving(player);
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
