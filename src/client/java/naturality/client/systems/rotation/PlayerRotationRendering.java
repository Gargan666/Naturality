package naturality.client.systems.rotation;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.ArrayList;
import java.util.List;
import naturality.api.worlds.LoadedChunks;
import naturality.systems.rotation.PlayerRotation;
import naturality.systems.rotation.PlayerRotation.Angles;
import naturality.systems.swimming.PlayerSwimming;
import naturality.client.systems.swimming.WaterSurfaceRendering;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.util.Mth;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/** Presentation of the common orientation; it never computes or advances physical rotation. */
@org.jspecify.annotations.NullMarked
public final class PlayerRotationRendering {
    private PlayerRotationRendering() { }
    private static final java.util.Map<Player, SpinExit> SPIN_EXITS = new java.util.WeakHashMap<>();
    private static final class SpinExit {
        private Angles rotation = Angles.ZERO;
        private float lastSpinTime;
    }
    /** Per-camera eye-offset handoff. Translation follows the player immediately; only pose changes ease. */
    public static final class CameraHandoff {
        private boolean spinning;
        private boolean settling;
        private double previousTime;
        private double exitTime;
        private Vec3 offset = Vec3.ZERO;

        public boolean active() { return spinning || settling; }
        public double release(double time) {
            double value = settling ? Math.clamp((time - exitTime - 10) / 10, 0, 1) : 0;
            return value * value * (3 - 2 * value);
        }
        public Vec3 sample(boolean spin, double time, Vec3 target) {
            if (spin || !active() || time < previousTime || time - previousTime > 5) {
                offset = target;
                settling = false;
            } else {
                if (spinning) exitTime = time;
                settling |= spinning;
                double elapsed = Math.max(0, time - previousTime);
                offset = offset.lerp(target, -Math.expm1(-0.4 * elapsed));
                if (offset.distanceToSqr(target) < 1.0e-8 || time - exitTime >= 20) {
                    offset = target;
                    settling = false;
                }
            }
            spinning = spin;
            previousTime = time;
            return offset.lerp(target, release(time));
        }
    }
    public static Vec3 cameraHandoff(Player player, float partial, Vec3 vanilla, CameraHandoff handoff) {
        boolean spin = naturality.systems.swimming.WaterSurface.riptiding(player);
        boolean rotated = PlayerRotation.terrainActive(player) || PlayerRotation.interpolatedAngles(player, partial).active();
        Vec3 center = player.getPosition(partial).add(0, player.getBbHeight() / 2.0, 0);
        Vec3 target = rotated ? player.getEyePosition(partial) : vanilla;
        if (!spin && handoff.active() && rotated) {
            // The render rotation still contains the upright transform from native spin.
            // Follow the resumed physical target through this one camera-specific filter.
            target = player.getPosition(partial).add(PlayerRotation.eyeOffset(player, player.getEyeHeight()))
                .lerp(target, handoff.release(player.tickCount + partial));
        }
        boolean easing = handoff.active();
        Vec3 eye = center.add(handoff.sample(spin, player.tickCount + partial, target.subtract(center)));
        return !spin && !player.isSpectator() && (rotated || easing) ? clearCameraPosition(player, center, eye) : eye;
    }
    /** Screen obstruction follows the rendered camera, not the unretracted gameplay eye. */
    public static @Nullable BlockState viewBlockingState(
            Player player, AABB nearPlane) {
        if (player.noPhysics) return null;
        for (var pos : BlockPos.betweenClosed(BlockPos.containing(nearPlane.minX, nearPlane.minY, nearPlane.minZ),
                BlockPos.containing(nearPlane.maxX, nearPlane.maxY, nearPlane.maxZ))) {
            if (!LoadedChunks.has(player.level(), pos)) continue;
            var block = player.level().getBlockState(pos);
            if (block.getRenderShape() != net.minecraft.world.level.block.RenderShape.INVISIBLE
                    && block.isViewBlocking(player.level(), pos, nearPlane)) return block;
        }
        return null;
    }
    public static boolean underwaterListener(Player player) {
        Vec3 listener = cameraPosition(player, 1);
        var pos = BlockPos.containing(listener);
        if (!LoadedChunks.has(player.level(), pos)) return false;
        var fluid = player.level().getFluidState(pos);
        return fluid.is(net.minecraft.tags.FluidTags.WATER)
            && listener.y < pos.getY() + fluid.getHeight(player.level(), pos);
    }
    /** Slide the rendered eye toward the torso along the hurtbox, without changing gameplay eyes. */
    public static Vec3 cameraPosition(Player player, float partial) {
        Vec3 eye = player.getEyePosition(partial);
        if (player.isSpectator()) return eye;
        Vec3 center = player.getPosition(partial).add(0, player.getBbHeight() / 2.0, 0);
        return clearCameraPosition(player, center, eye);
    }
    private static Vec3 clearCameraPosition(Player player, Vec3 center, Vec3 eye) {
        var area = new AABB(center, eye).inflate(0.15);
        if (!LoadedChunks.has(player.level(), BlockPos.containing(area.minX, area.minY, area.minZ))
                || !LoadedChunks.has(player.level(), BlockPos.containing(area.maxX, area.maxY, area.maxZ))) return center;
        var obstacles = new ArrayList<AABB>();
        for (var shape : player.level().getBlockCollisions(player, area)) obstacles.addAll(shape.toAabbs());
        return clearCameraPosition(center, eye, obstacles);
    }
    public static Vec3 clearCameraPosition(Vec3 center, Vec3 eye, List<AABB> obstacles) {
        var probe = new PlayerRotation.Box(center, new Vec3(0.15, 0.15, 0.15), Angles.ZERO);
        Vec3 direction = eye.subtract(center);
        double fraction = 1;
        for (var obstacle : obstacles) fraction = Math.min(fraction, probe.sweep(obstacle, direction).fraction());
        if (fraction == 1) return eye;
        return center.add(direction.scale(Math.max(0, fraction - 1e-5 / Math.max(1e-5, direction.length()))));
    }
    public interface State {
        float naturality$rotationYaw();
        void naturality$rotationYaw(float yaw);
        Angles naturality$bodyRotation();
        void naturality$bodyRotation(Angles angles);
        float naturality$rotationPivot();
        void naturality$rotationPivot(float height);
    }
    public static void extract(Player player, AvatarRenderState state, float partial) {
        ((State)state).naturality$rotationYaw(state.bodyRot);
        float time = player.tickCount + partial;
        if (naturality.systems.swimming.WaterSurface.riptiding(player)) {
            state.isAutoSpinAttack = true;
            // AvatarRenderer otherwise adds its interpolated swim tilt AFTER the native spin.
            state.swimAmount = 0;
            state.isVisuallySwimming = false;
            ((State)state).naturality$bodyRotation(Angles.ZERO);
            var exit = SPIN_EXITS.computeIfAbsent(player, _ -> new SpinExit());
            double yaw = Math.toRadians(180 - state.bodyRot);
            exit.rotation = Angles.fromQuaternion(new org.joml.Quaterniond().rotationY(yaw)
                .rotateX(Math.toRadians(-90 - state.xRot)).rotateY(Math.toRadians(state.ageInTicks * -75))
                .rotateY(-yaw));
            exit.lastSpinTime = time;
            return;
        }
        var physical = PlayerRotation.interpolatedAngles(player, partial);
        var lean = ((WaterSurfaceRendering.State)state).naturality$surfaceLean();
        double radians = lean.length();
        var surface = radians < 1.0e-6 ? Angles.ZERO : Angles.fromQuaternion(
            new org.joml.Quaterniond().rotationAxis(radians, lean.x / radians, 0, lean.z / radians));
        float blend = PlayerSwimming.surfaceBlend(player, partial);
        var angles = physical.towards(surface, blend);
        var exit = SPIN_EXITS.get(player);
        if (exit != null) {
            // Network interpolation still contains the zero transform used during native spin.
            // Blend the captured spin straight to the resumed swimming body instead.
            if (PlayerSwimming.active(player)) angles = PlayerRotation.angles(player).towards(surface, blend);
            else if (naturality.systems.swimming.WaterSurface.fullySubmerged(player))
                angles = PlayerSwimming.targetAngles(player.getLookAngle(), player.getYRot(), true, false, true, true);
            double progress = Math.clamp((time - exit.lastSpinTime) / 8.0, 0, 1);
            angles = exit.rotation.towards(angles, progress * progress * (3 - 2 * progress));
            if (progress >= 1) SPIN_EXITS.remove(player);
        }
        ((State)state).naturality$bodyRotation(angles);
        ((State)state).naturality$rotationPivot(Mth.lerp(blend, player.getBbHeight() / 2, player.getEyeHeight()));
        if (!angles.active()) return;
        var head = headPose(angles, player.getViewVector(partial), state.bodyRot);
        // Release the constraint gradually as the torso returns to vanilla's upright pose.
        float amount = (float)Math.clamp(angles.radiansTo(Angles.ZERO) / Math.toRadians(15), 0, 1);
        state.yRot = Mth.lerp(amount, state.yRot, head.y);
        state.xRot = Mth.lerp(amount, state.xRot, head.x);
    }
    /** Pitch/yaw in degrees relative to the torso, with a soft neck range and no rear/pole branch cut. */
    public static Vec2 headPose(Angles angles, Vec3 view, float bodyYaw) {
        Vec3 local = new Vec3(view.dot(angles.transform(new Vec3(1, 0, 0))),
            view.dot(angles.transform(new Vec3(0, 1, 0))), view.dot(angles.transform(new Vec3(0, 0, 1))));
        double yaw = Math.toRadians(bodyYaw);
        double side = local.x * Math.cos(yaw) + local.z * Math.sin(yaw);
        double forward = -local.x * Math.sin(yaw) + local.z * Math.cos(yaw);
        // A positive forward bias makes looking behind the torso relax toward centre instead of
        // choosing either +/-180 degrees. It also fades yaw naturally when looking along the neck.
        double front = Math.max(0, forward);
        double headYaw = Math.toDegrees(Math.atan2(-side, Math.sqrt(front * front + 0.25 * 0.25)));
        double pitch = Math.toDegrees(Math.atan2(-local.y, Math.hypot(side, forward)));
        return new Vec2((float)(55 * Math.tanh(pitch / 55)), (float)(60 * Math.tanh(headYaw / 60)));
    }
    public static void rotate(AvatarRenderState state, PoseStack pose, float bodyYaw, float scale) {
        var angles = relativeRotation(((State)state).naturality$bodyRotation(), ((State)state).naturality$rotationYaw(), bodyYaw);
        double half = ((State)state).naturality$rotationPivot() / scale;
        pose.translate(0, half, 0);
        pose.rotateDegrees(Axis.ZP, angles.z());
        pose.rotateDegrees(Axis.XP, angles.x());
        pose.rotateDegrees(Axis.YP, angles.yawCompensation());
        pose.translate(0, -half, 0);
        pose.rotateDegrees(Axis.YP, 180 - bodyYaw);
    }
    /** GUI previews replace body yaw after extraction; carry the world tilt into that new facing frame. */
    public static Angles relativeRotation(Angles angles, float sourceYaw, float displayedYaw) {
        double difference = Math.toRadians(sourceYaw - displayedYaw);
        if (Math.abs(difference) < 1.0e-7) return angles;
        return Angles.fromQuaternion(new org.joml.Quaterniond().rotationY(difference)
            .mul(angles.quaternion()).rotateY(-difference));
    }
    /** F3+B distinguishes the oriented target (white/green) from terrain collision (cyan). */
    public static void debug(Player player, float partial, boolean server) {
        var body = PlayerRotation.body(player).move(player.getPosition(partial).subtract(player.position()));
        debugBox(body, server ? 0xff00ff00 : 0xffffffff);
        debugBox(PlayerRotation.terrain(body), 0xff00ffff);
        var eye = player.getEyePosition(partial);
        Gizmos.point(eye, 0xffff0000, 3);
        Gizmos.point(body.center.add(body.orientation.transform(new Vec3(0, -body.half.y, 0))), 0xff00ff00, 3);
        Gizmos.arrow(eye, eye.add(player.getViewVector(partial).scale(2)), 0xff0000ff);
    }
    private static void debugBox(PlayerRotation.Box body, int color) {
        var corners = new Vec3[8];
        for (int i = 0; i < 8; i++) {
            corners[i] = body.center.add(body.orientation.transform(new Vec3(
                (i & 1) == 0 ? -body.half.x : body.half.x,
                (i & 2) == 0 ? -body.half.y : body.half.y,
                (i & 4) == 0 ? -body.half.z : body.half.z)));
        }
        for (int i = 0; i < 8; i++) for (int bit : new int[]{1, 2, 4}) {
            if ((i & bit) == 0) Gizmos.line(corners[i], corners[i | bit], color);
        }
    }
}
