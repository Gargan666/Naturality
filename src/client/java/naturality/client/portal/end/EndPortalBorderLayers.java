package naturality.client.portal.end;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.renderpearl.api.pipeline.*;
import naturality.Naturality;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.oit.OitPipelineSet;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;

public final class EndPortalBorderLayers {
    private static final DepthStencilState DEPTH = new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false);
    public static final RenderType BORDER = create("end_portal_border");

    private static RenderType create(String name) {
        var effect = RenderPipeline.builder()
            .withBindGroupLayout(net.minecraft.client.renderer.BindGroupLayouts.SAMPLER1)
            .withVertexShader(Naturality.id("core/end_portal_wall"))
            .withFragmentShader(Naturality.id("core/end_portal_wall"))
            .withVertexBinding(0, DefaultVertexFormat.ENTITY)
            .withCull(false).buildSnippet();
        var pipeline = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.PARTICLE_SNIPPET, effect)
            .withLocation(Naturality.id("pipeline/" + name)).withDepthStencilState(DEPTH)
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT)).build());
        var oit = RenderPipelines.register(OitPipelineSet.builder("naturality_" + name,
            RenderPipeline.builder(RenderPipelines.OIT_PARTICLE_SNIPPET, RenderPipelines.GLOBALS_SNIPPET, effect))
            .withDepthBoundsModifier(b -> b.withDepthStencilState(DEPTH))
            .withTransmittanceModifier(b -> b.withDepthStencilState(DEPTH))
            .withAccumulateModifier(b -> b.withDepthStencilState(DEPTH)).build());
        return RenderType.create("naturality_" + name, RenderSetup.builder(pipeline)
            .withTexture("Sampler0", naturality.client.AtlasLocations.BLOCKS).useLightmap()
            .withTexture("Sampler1", EndPortalGlowPalette.TEXTURE)
            .setOitPipelines(oit).sortOnUpload().createRenderSetup());
    }

    public static void initialize() { }
    private EndPortalBorderLayers() { }
}
