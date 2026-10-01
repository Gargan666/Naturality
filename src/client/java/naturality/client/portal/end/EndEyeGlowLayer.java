package naturality.client.portal.end;

import com.mojang.renderpearl.api.pipeline.*;
import naturality.Naturality;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.oit.OitPipelineSet;

/** Independent transient effect; shares only the sampled color texture. */
public final class EndEyeGlowLayer {
    private static final DepthStencilState DEPTH = new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false);
    private static final RenderPipeline.Snippet EFFECT = RenderPipeline.builder()
        .withVertexShader(Naturality.id("core/portal_opening"))
        .withFragmentShader(Naturality.id("core/end_eye_glow"))
        .withCull(false).buildSnippet();
    private static final RenderPipeline PIPELINE = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.PARTICLE_SNIPPET, EFFECT)
            .withLocation(Naturality.id("pipeline/end_eye_glow"))
            .withDepthStencilState(DEPTH)
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT)).build());
    private static final OitPipelineSet OIT = RenderPipelines.register(
        OitPipelineSet.builder("naturality_end_eye_glow",
            RenderPipeline.builder(RenderPipelines.OIT_PARTICLE_SNIPPET, RenderPipelines.GLOBALS_SNIPPET, EFFECT))
            .withDepthBoundsModifier(b -> b.withDepthStencilState(DEPTH))
            .withTransmittanceModifier(b -> b.withDepthStencilState(DEPTH))
            .withAccumulateModifier(b -> b.withDepthStencilState(DEPTH)).build());
    public static final SingleQuadParticle.Layer LAYER =
        new SingleQuadParticle.Layer(true, EndPortalGlowPalette.TEXTURE, PIPELINE, OIT);
    public static void initialize() { }
    private EndEyeGlowLayer() { }
}
