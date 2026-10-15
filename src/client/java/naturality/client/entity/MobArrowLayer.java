package naturality.client.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.ArrayList;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.object.projectile.ArrowModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.TippableArrowRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.ArrowRenderState;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Vanilla arrow placement extended to arbitrary, hierarchically animated mob models. */
public final class MobArrowLayer<S extends @org.jspecify.annotations.NonNull LivingEntityRenderState, M extends @org.jspecify.annotations.NonNull EntityModel<? super S>> extends RenderLayer<S, M> {
    private final ArrowModel arrow;
    private final ArrowRenderState arrowState = new ArrowRenderState();

    public MobArrowLayer(RenderLayerParent<S, M> parent, EntityRendererProvider.Context context) {
        super(parent);
        arrow = new ArrowModel(context.bakeLayer(ModelLayers.ARROW));
    }

    @Override public void submit(PoseStack stack, SubmitNodeCollector collector, int light, S state, float yaw, float pitch) {
        int count = ((EmbeddedArrowState)state).naturality$arrowCount();
        // Players already have their vanilla ArrowLayer.
        if (count <= 0 || state instanceof AvatarRenderState || state.isInvisible) return;
        var root = getParentModel().root();
        var targets = new ArrayList<Target>();
        root.visit(stack, (pose, path, index, cube) -> {
            ModelPart part = root;
            if (!part.visible) return;
            for (String name : path.split("/")) {
                if (name.isEmpty()) continue;
                part = part.getChild(name);
                if (!part.visible) return;
            }
            if (!part.skipDraw) targets.add(new Target(pose.copy(), cube, path, index));
        });
        if (targets.isEmpty()) return;
        var random = RandomSource.createThreadLocalInstance(((EmbeddedArrowState)state).naturality$arrowSeed());
        for (int i = 0; i < count; i++) {
            Target target = targets.get(random.nextInt(targets.size()));
            float x = random.nextFloat(), y = random.nextFloat(), z = random.nextFloat();
            var hits = ((EmbeddedArrowState)state).naturality$impacts();
            var impact = i < hits.size() ? hits.get(i) : null;
            if (impact != null && impact.path == null) bindImpact(impact, targets, state);
            if (impact != null && impact.path != null) {
                target = null;
                for (Target candidate : targets) {
                    if (candidate.path.equals(impact.path) && candidate.index == impact.cubeIndex) {
                        target = candidate;
                        break;
                    }
                }
                // A hidden attached limb should hide its arrow too.
                if (target == null) continue;
            }
            stack.pushPose();
            stack.last().pose().set(target.pose.pose());
            stack.last().normal().set(target.pose.normal());
            ModelPart.Cube cube = target.cube;
            if (impact != null && impact.point != null) {
                stack.translate(impact.point.x, impact.point.y, impact.point.z);
            } else {
                stack.translate(Mth.lerp(x, cube.minX, cube.maxX) / 16.0F,
                    Mth.lerp(y, cube.minY, cube.maxY) / 16.0F, Mth.lerp(z, cube.minZ, cube.maxZ) / 16.0F);
            }
            removeModelScale(stack.last());
            float dx = 1 - 2 * x, dy = 1 - 2 * y, dz = 1 - 2 * z;
            if (impact != null && impact.direction != null) {
                dx = impact.direction.x; dy = impact.direction.y; dz = impact.direction.z;
            }
            stack.rotateDegrees(Axis.YP, (float)Math.toDegrees(Math.atan2(dx, dz)) - 90);
            stack.rotateDegrees(Axis.ZP, (float)Math.toDegrees(Math.atan2(dy, Mth.sqrt(dx * dx + dz * dz))));
            collector.submitModel(arrow, arrowState, stack, TippableArrowRenderer.NORMAL_ARROW_LOCATION,
                light, OverlayTexture.NO_OVERLAY, state.outlineColor);
            stack.popPose();
        }
    }

    private static void bindImpact(MobArrowImpacts.Impact impact, java.util.List<Target> targets, LivingEntityRenderState state) {
        var hit = impact.hit;
        // Rotate the recorded offset with the body if it turned before the first rendered frame.
        float turn = (float)Math.toRadians(hit.bodyYaw() - state.bodyRot);
        var offset = new Vector3f((float)hit.offset().x, (float)hit.offset().y, (float)hit.offset().z).rotateY(turn);
        var incoming = new Vector3f((float)hit.direction().x, (float)hit.direction().y, (float)hit.direction().z).rotateY(turn);
        var origin = ((EmbeddedArrowState)state).naturality$origin();
        Vector3f point = origin.transformPosition(offset, new Vector3f());
        Vector3f direction = origin.transformDirection(incoming, new Vector3f());
        float bestDistance = Float.POSITIVE_INFINITY;
        for (Target target : targets) {
            Matrix4f matrix = target.pose.pose();
            if (Math.abs(matrix.determinant()) < 1.0e-12F) continue;
            var inverse = new Matrix4f(matrix).invert();
            Vector3f localPoint = inverse.transformPosition(point, new Vector3f());
            Vector3f localDirection = inverse.transformDirection(direction, new Vector3f());
            var cube = target.cube;
            Vector3f min = new Vector3f(cube.minX, cube.minY, cube.minZ).div(16);
            Vector3f max = new Vector3f(cube.maxX, cube.maxY, cube.maxZ).div(16);
            // Only consider faces pointing toward the shooter, never the exit side.
            for (int axis = 0; axis < 3; axis++) {
                if (Math.abs(localDirection.get(axis)) < 1.0e-6F) continue;
                Vector3f surface = incomingFace(localPoint, localDirection, min, max, axis);
                float distance = matrix.transformPosition(surface, new Vector3f()).distanceSquared(point);
                if (distance < bestDistance) {
                    bestDistance = distance;
                    impact.path = target.path;
                    impact.cubeIndex = target.index;
                    impact.point = surface;
                    // Use the unscaled part basis to preserve the world-space flight direction.
                    var unitPose = target.pose.copy();
                    removeModelScale(unitPose);
                    impact.direction = new Matrix4f(unitPose.pose()).invert().transformDirection(direction, new Vector3f()).normalize();
                }
            }
        }
    }

    public static Vector3f incomingFace(Vector3f point, Vector3f direction, Vector3f min, Vector3f max, int axis) {
        var surface = new Vector3f(point).max(min).min(max);
        surface.setComponent(axis, direction.get(axis) > 0 ? min.get(axis) : max.get(axis));
        return surface;
    }

    /** Keep the transformed attachment position, but give the arrow a unit-size basis. */
    public static void removeModelScale(PoseStack.Pose pose) {
        Matrix4f matrix = pose.pose();
        var x = new Vector3f(matrix.m00(), matrix.m01(), matrix.m02());
        var y = new Vector3f(matrix.m10(), matrix.m11(), matrix.m12());
        var oldZ = new Vector3f(matrix.m20(), matrix.m21(), matrix.m22());
        if (x.lengthSquared() < 1.0e-12F || y.lengthSquared() < 1.0e-12F || oldZ.lengthSquared() < 1.0e-12F) return;
        x.normalize();
        y.sub(new Vector3f(x).mul(x.dot(y)));
        if (y.lengthSquared() < 1.0e-12F) return;
        y.normalize();
        var z = new Vector3f(x).cross(y);
        // Preserve mirrored model coordinates, including LivingEntityRenderer's axis flips.
        if (z.dot(oldZ) < 0) z.negate();
        matrix.m00(x.x).m01(x.y).m02(x.z);
        matrix.m10(y.x).m11(y.y).m12(y.z);
        matrix.m20(z.x).m21(z.y).m22(z.z);
        pose.normal().set(matrix);
    }

    private record Target(PoseStack.Pose pose, ModelPart.Cube cube, String path, int index) {}
}
