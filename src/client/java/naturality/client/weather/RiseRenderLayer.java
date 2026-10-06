package naturality.client.weather;

import com.mojang.renderpearl.api.pipeline.*;
import naturality.Naturality;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.oit.OitPipelineSet;

/** Palette-only cards, with physical width clipped in the fragment shader. */
public final class RiseRenderLayer {
    private static final RenderPipeline PIPELINE=RenderPipelines.register(RenderPipeline.builder(RenderPipelines.PARTICLE_SNIPPET)
        .withLocation(Naturality.id("pipeline/rise"))
        .withVertexShader(Naturality.id("core/portal_opening"))
        .withFragmentShader(Naturality.id("core/rise"))
        .withCull(false).withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL,false))
        .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT)).build());
    private static final OitPipelineSet OIT=RenderPipelines.register(OitPipelineSet.builder("naturality_rise",
        RenderPipeline.builder(RenderPipelines.OIT_PARTICLE_SNIPPET)
            .withVertexShader(Naturality.id("core/portal_opening"))
            .withFragmentShader(Naturality.id("core/rise")).withCull(false)).build());
    public static final SingleQuadParticle.Layer LAYER=new SingleQuadParticle.Layer(
        true,Naturality.id("textures/sky/aurora_end.png"),PIPELINE,OIT);
    private RiseRenderLayer() {}
    public static void initialize() {}
}