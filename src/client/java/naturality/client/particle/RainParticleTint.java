package naturality.client.particle;

import com.mojang.renderpearl.api.pipeline.*;
import naturality.Naturality;

import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.oit.OitPipelineSet;

/** Separate shaders neutralize only water sprites, including resource-pack replacements. */
public final class RainParticleTint {
    private static final RenderPipeline.Snippet EFFECT=RenderPipeline.builder()
        .withFragmentShader(Naturality.id("core/rain_particle")).buildSnippet();
    private static final RenderPipeline TRANSLUCENT=RenderPipelines.register(RenderPipeline.builder(RenderPipelines.PARTICLE_SNIPPET,EFFECT)
        .withLocation(Naturality.id("pipeline/rain_particle_translucent"))
        .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT)).build());
    private static final OitPipelineSet OIT=RenderPipelines.register(OitPipelineSet.builder("naturality_rain_particle",
        RenderPipeline.builder(RenderPipelines.OIT_PARTICLE_SNIPPET,EFFECT)).build());
    public static final SingleQuadParticle.Layer TRANSPARENT=new SingleQuadParticle.Layer(true,naturality.client.AtlasLocations.PARTICLES,TRANSLUCENT,OIT);
    public static void initialize() {}
}

