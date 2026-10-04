package naturality.client.villager;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;

/** Geometry and UVs from resources/entity models/fishing_hook.java (Blockbench). */
public final class FishingBobberModel {
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath("naturality", "textures/entity/fishing_hook.png");
    // Item glint normally culls back faces. The authored fin has zero thickness,
    // so the bobber needs the same double-sided rendering as its plain material.
    private static final net.minecraft.client.renderer.rendertype.RenderType ENCHANTED =
        net.minecraft.client.renderer.rendertype.RenderType.create("naturality_bobber_glint",
            net.minecraft.client.renderer.rendertype.RenderSetup.builder(
                net.minecraft.client.renderer.RenderPipelines.register(
                    com.mojang.renderpearl.api.pipeline.RenderPipeline.builder(
                        net.minecraft.client.renderer.RenderPipelines.ITEM_SNIPPET,
                        net.minecraft.client.renderer.RenderPipelines.GLINT_SNIPPET)
                    .withLocation(Identifier.fromNamespaceAndPath("naturality", "pipeline/bobber_glint"))
                    .withShaderDefine("ALPHA_CUTOUT", .1F).withCull(false)
                    .withColorTargetState(com.mojang.renderpearl.api.pipeline.ColorTargetState.DEFAULT).build()))
                .withTexture("Sampler0", TEXTURE)
                .withTexture("GlintSampler", net.minecraft.client.renderer.feature.ItemFeatureRenderer.ENCHANTED_GLINT_ITEM)
                .setTextureTransform(net.minecraft.client.renderer.rendertype.TextureTransform.GLINT_TEXTURING)
                .useLightmap().useOverlay().createRenderSetup());
    private static final ModelPart PART = createLayer().bakeRoot().getChild("bb_main");

    private FishingBobberModel() { }

    private static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        mesh.getRoot().addOrReplaceChild("bb_main", CubeListBuilder.create()
            .texOffs(0, 0).addBox(-1.5F, -3.0F, -1.5F, 3.0F, 3.0F, 3.0F, new CubeDeformation(0))
            .texOffs(0, 6).addBox(-2.5F, -4.0F, 0.0F, 3.0F, 7.0F, 0.0F, new CubeDeformation(0)),
            PartPose.ZERO);
        return LayerDefinition.create(mesh, 16, 16);
    }

    public static void submit(PoseStack pose, SubmitNodeCollector collector, int light) {
        submit(pose, collector, light, false);
    }

    public static boolean rodGlint(net.minecraft.world.entity.LivingEntity owner) {
        if (owner == null) return false;
        var rod = owner.getMainHandItem().is(net.minecraft.world.item.Items.FISHING_ROD)
            ? owner.getMainHandItem() : owner.getOffhandItem();
        return rod.is(net.minecraft.world.item.Items.FISHING_ROD) && rod.hasFoil();
    }

    public static void submit(PoseStack pose, SubmitNodeCollector collector, int light, boolean glint) {
        pose.pushPose();
        pose.translate(0, .07F, 0);
        // ModelPart converts Blockbench's pixel coordinates to blocks itself.
        pose.scale(-1.3F, -1.3F, 1.3F);
        collector.submitCustomGeometry(pose, glint ? ENCHANTED : RenderTypes.entityCutout(TEXTURE), (submitted, buffer) -> {
            PoseStack modelPose = new PoseStack();
            modelPose.last().pose().set(submitted.pose());
            modelPose.last().normal().set(submitted.normal());
            PART.render(modelPose, glint ? naturality.client.glint.GlintContours.capture(buffer, TEXTURE, false) : buffer,
                light, OverlayTexture.NO_OVERLAY);
        });
        pose.popPose();
    }
}
