package naturality.client.api.rendering;

import com.mojang.renderpearl.api.pipeline.*;
import naturality.Naturality;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.oit.OitPipelineSet;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;

/** Shared End-eye and bookshelf insertion pulse: identical fade, wave and desaturation. */
public final class PlacementGlow {
    public static final int DURATION_TICKS = 40;
    private PlacementGlow() {}
    public static float envelope(float age) {
        float t = Math.clamp(age / DURATION_TICKS, 0, 1);
        return 1 - t * t * (3 - 2 * t);
    }
    public record Layers(RenderPipeline pipeline, OitPipelineSet oit) {
        public SingleQuadParticle.Layer particles(Identifier palette) {
            return new SingleQuadParticle.Layer(true, palette, pipeline, oit);
        }
        public RenderType geometry(String name, Identifier palette) {
            return RenderType.create(name, RenderSetup.builder(pipeline).withTexture("Sampler0", palette)
                .useLightmap().setOitPipelines(oit).sortOnUpload().createRenderSetup());
        }
    }
    public static Layers create(String name) {
        return create(name, false);
    }
    public static Layers create(String name, boolean pixelAligned) {
        var depth = new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false);
        var effectBuilder = RenderPipeline.builder().withVertexShader(Naturality.id("core/portal_opening"))
            .withFragmentShader(Naturality.id("core/end_eye_glow")).withCull(false);
        if (pixelAligned) effectBuilder.withShaderDefine("NATURALITY_PIXEL_GLOW");
        var effect = effectBuilder.buildSnippet();
        var pipeline = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.PARTICLE_SNIPPET, effect)
            .withLocation(Naturality.id("pipeline/" + name)).withDepthStencilState(depth)
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT)).build());
        var oit = RenderPipelines.register(OitPipelineSet.builder("naturality_" + name,
                RenderPipeline.builder(RenderPipelines.OIT_PARTICLE_SNIPPET, RenderPipelines.GLOBALS_SNIPPET, effect))
            .withDepthBoundsModifier(b -> b.withDepthStencilState(depth))
            .withTransmittanceModifier(b -> b.withDepthStencilState(depth))
            .withAccumulateModifier(b -> b.withDepthStencilState(depth)).build());
        return new Layers(pipeline, oit);
    }
}
