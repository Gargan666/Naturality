package naturality.client.systems.rotation;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import naturality.systems.rotation.PlayerRotation;
import naturality.systems.rotation.PlayerRotation.Angles;
import naturality.systems.swimming.PlayerSwimming;
import naturality.client.systems.swimming.WaterSurfaceRendering;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.util.Mth;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.Vec2;

/** Presentation of the common orientation; it never computes or advances physical rotation. */
@org.jspecify.annotations.NullMarked
public final class PlayerRotationRendering {
    private PlayerRotationRendering() { }
    public interface State {
        Angles naturality$bodyRotation();
        void naturality$bodyRotation(Angles angles);
        float naturality$rotationPivot();
        void naturality$rotationPivot(float height);
    }
    public static void extract(Player player, AvatarRenderState state, float partial) {
        var physical = PlayerRotation.interpolatedAngles(player, partial);
        var lean = ((WaterSurfaceRendering.State)state).naturality$surfaceLean();
        double radians = lean.length();
        var surface = radians < 1.0e-6 ? Angles.ZERO : Angles.fromQuaternion(
            new org.joml.Quaterniond().rotationAxis(radians, lean.x / radians, 0, lean.z / radians));
        float blend = PlayerSwimming.surfaceBlend(player, partial);
        var angles = physical.towards(surface, blend);
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
        var angles = ((State)state).naturality$bodyRotation();
        double half = ((State)state).naturality$rotationPivot() / scale;
        pose.translate(0, half, 0);
        pose.rotateDegrees(Axis.ZP, angles.z());
        pose.rotateDegrees(Axis.XP, angles.x());
        pose.rotateDegrees(Axis.YP, angles.yawCompensation());
        pose.translate(0, -half, 0);
        pose.rotateDegrees(Axis.YP, 180 - bodyYaw);
    }
    /** F3+B displays the real collision box, plus the transformed eyes and feet. */
    public static void debug(Player player, float partial, boolean server) {
        var body = PlayerRotation.body(player).move(player.getPosition(partial).subtract(player.position()));
        var corners = new Vec3[8];
        int color = server ? 0xff00ff00 : 0xffffffff;
        for (int i = 0; i < 8; i++) {
            corners[i] = body.center.add(body.orientation.transform(new Vec3(
                (i & 1) == 0 ? -body.half.x : body.half.x,
                (i & 2) == 0 ? -body.half.y : body.half.y,
                (i & 4) == 0 ? -body.half.z : body.half.z)));
        }
        for (int i = 0; i < 8; i++) for (int bit : new int[]{1, 2, 4}) {
            if ((i & bit) == 0) Gizmos.line(corners[i], corners[i | bit], color);
        }
        var eye = player.getEyePosition(partial);
        Gizmos.point(eye, 0xffff0000, 3);
        Gizmos.point(body.center.add(body.orientation.transform(new Vec3(0, -body.half.y, 0))), 0xff00ff00, 3);
        Gizmos.arrow(eye, eye.add(player.getViewVector(partial).scale(2)), 0xff0000ff);
    }
}
