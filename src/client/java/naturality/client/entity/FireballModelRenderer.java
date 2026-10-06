package naturality.client.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.projectile.hurtingprojectile.Fireball;
import naturality.Naturality;

/** The supplied Blockbench cubes, split into a solid core and scrolling shell. */
public final class FireballModelRenderer<T extends Fireball> extends EntityRenderer<T, FireballModelRenderer.State> {
    private static final ModelPart CORE = core();
    private final float scale;

    public static final class State extends EntityRenderState {
        public float yaw;
        public float pitch;
    }

    public FireballModelRenderer(EntityRendererProvider.Context context, float scale) {
        super(context);
        this.scale = scale;
    }

    private static ModelPart core() {
        MeshDefinition mesh = new MeshDefinition();
        // Center the original (-4,-8,-4) core on the shared pivot.
        mesh.getRoot().addOrReplaceChild("cube", CubeListBuilder.create()
            .texOffs(0, 20).addBox(-4, -4, -4, 8, 8, 8), PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 64).bakeRoot();
    }

    @Override public State createRenderState() { return new State(); }

    @Override public void extractRenderState(T entity, State state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.displayFireAnimation = false;
        state.yaw = entity.getYRot(partialTick);
        state.pitch = entity.getXRot(partialTick);
    }

    /** One whole source texel every two ticks; wrapping keeps the render-type set bounded. */
    public static float fireOffset(float age) {
        return Math.floorMod((int)Math.floor(age / 2), 44) / 44.0F;
    }

    @Override public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        if (!state.isInvisible) {
            pose.pushPose();
            pose.translate(0, state.boundingBoxHeight / 2, 0);
            pose.rotate(Axis.YP.rotationDegrees(-state.yaw));
            pose.rotate(Axis.XP.rotationDegrees(state.pitch));
            // Local forward is +Z: positive X tumbles the top forward, negative Y turns left.
            pose.rotate(Axis.YP.rotationDegrees(-state.ageInTicks * 6));
            pose.rotate(Axis.XP.rotationDegrees(state.ageInTicks * 9));
            pose.scale(-scale, -scale, scale);
            submitPart(CORE, pose, collector, RenderTypes.entityCutout(Naturality.id("textures/entity/fireball.png")));
            FireballShell.submit(pose, collector, state.ageInTicks);
            pose.popPose();
        }
        super.submit(state, pose, collector, camera);
    }

    private static void submitPart(ModelPart part, PoseStack pose, net.minecraft.client.renderer.OrderedSubmitNodeCollector collector,
            net.minecraft.client.renderer.rendertype.RenderType type) {
        collector.submitCustomGeometry(pose, type, (submitted, buffer) -> {
            PoseStack modelPose = new PoseStack();
            modelPose.last().pose().set(submitted.pose());
            modelPose.last().normal().set(submitted.normal());
            part.render(modelPose, buffer, 15728880, OverlayTexture.NO_OVERLAY);
        });
    }
}


