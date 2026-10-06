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

/** Vanilla arrow placement extended to arbitrary, hierarchically animated mob models. */
public final class MobArrowLayer<S extends LivingEntityRenderState, M extends EntityModel<? super S>> extends RenderLayer<S, M> {
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
            if (!part.skipDraw) targets.add(new Target(pose.copy(), cube));
        });
        if (targets.isEmpty()) return;
        var random = RandomSource.createThreadLocalInstance(((EmbeddedArrowState)state).naturality$arrowSeed());
        for (int i = 0; i < count; i++) {
            Target target = targets.get(random.nextInt(targets.size()));
            float x = random.nextFloat(), y = random.nextFloat(), z = random.nextFloat();
            stack.pushPose();
            stack.last().pose().set(target.pose.pose());
            stack.last().normal().set(target.pose.normal());
            ModelPart.Cube cube = target.cube;
            stack.translate(Mth.lerp(x, cube.minX, cube.maxX) / 16.0F,
                Mth.lerp(y, cube.minY, cube.maxY) / 16.0F, Mth.lerp(z, cube.minZ, cube.maxZ) / 16.0F);
            float dx = 1 - 2 * x, dy = 1 - 2 * y, dz = 1 - 2 * z;
            stack.rotateDegrees(Axis.YP, (float)Math.toDegrees(Math.atan2(dx, dz)) - 90);
            stack.rotateDegrees(Axis.ZP, (float)Math.toDegrees(Math.atan2(dy, Mth.sqrt(dx * dx + dz * dz))));
            collector.submitModel(arrow, arrowState, stack, TippableArrowRenderer.NORMAL_ARROW_LOCATION,
                light, OverlayTexture.NO_OVERLAY, state.outlineColor);
            stack.popPose();
        }
    }

    private record Target(PoseStack.Pose pose, ModelPart.Cube cube) {}
}
