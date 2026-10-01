package naturality.client.particle;

import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import naturality.Naturality;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.oit.OitPipelineSet;

public final class PortalGlowRenderLayer {
    // Geometry has a tiny fixed separation from its supporting frame face.
    // Use unbiased depth in every pass so foreground blocks still occlude it.
    private static final DepthStencilState DEPTH =
        new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false);
    private static final RenderPipeline.Snippet EFFECT = RenderPipeline.builder()
        .withVertexShader(Naturality.id("core/portal_opening"))
        .withFragmentShader(Naturality.id("core/portal_glow"))
        .withShaderDefine("GLOW_SLICES", PortalGlowGeometry.SLICES_PER_BLOCK)
        .withShaderDefine("GLOW_WAVE_AMPLITUDE", PortalGlowGeometry.WAVE_AMPLITUDE)
        .withShaderDefine("GLOW_BASE_REACH", PortalGlowGeometry.BASE_REACH)
        .withShaderDefine("GLOW_PORTAL_SURFACE", PortalGlowGeometry.PORTAL_SURFACE)
        .buildSnippet();
    private static final RenderPipeline PIPELINE = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.PARTICLE_SNIPPET, EFFECT)
            .withLocation(Naturality.id("pipeline/portal_glow"))
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withDepthStencilState(DEPTH)
            .build());
    private static final OitPipelineSet OIT = RenderPipelines.register(
        OitPipelineSet.builder("naturality_portal_glow",
                RenderPipeline.builder(RenderPipelines.OIT_PARTICLE_SNIPPET, RenderPipelines.GLOBALS_SNIPPET, EFFECT))
            .withDepthBoundsModifier(builder -> builder.withDepthStencilState(DEPTH))
            .withTransmittanceModifier(builder -> builder.withDepthStencilState(DEPTH))
            .withAccumulateModifier(builder -> builder.withDepthStencilState(DEPTH))
            .build());
    public static final SingleQuadParticle.Layer LAYER =
        new SingleQuadParticle.Layer(true, PortalGlowAppearance.PALETTE_TEXTURE, PIPELINE, OIT);

    private static final RenderPipeline MOTE_PIPELINE = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.PARTICLE_SNIPPET)
            .withLocation(Naturality.id("pipeline/portal_mote"))
            .withVertexShader(Naturality.id("core/portal_opening"))
            .withFragmentShader(Naturality.id("core/portal_mote"))
            .withCull(false)
            .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT)).build());
    private static final OitPipelineSet MOTE_OIT = RenderPipelines.register(
        OitPipelineSet.builder("naturality_portal_mote",
            RenderPipeline.builder(RenderPipelines.OIT_PARTICLE_SNIPPET)
                .withVertexShader(Naturality.id("core/portal_opening"))
                .withFragmentShader(Naturality.id("core/portal_mote")).withCull(false)).build());
    public static final SingleQuadParticle.Layer MOTE_LAYER = new SingleQuadParticle.Layer(
        true, PortalGlowAppearance.PALETTE_TEXTURE, MOTE_PIPELINE, MOTE_OIT);

    private PortalGlowRenderLayer() { }

    public static void initialize() {
        // Eager registration before the game's shader resource reload.
    }
}
