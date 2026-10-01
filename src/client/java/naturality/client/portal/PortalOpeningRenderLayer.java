package naturality.client.portal;

import com.mojang.renderpearl.api.pipeline.*;
import naturality.Naturality;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.oit.OitPipelineSet;

public final class PortalOpeningRenderLayer {
    public static final SingleQuadParticle.Layer PORTAL = layer("portal_opening");
    public static final SingleQuadParticle.Layer SOLID_PORTAL = solidPortal();
    public static final SingleQuadParticle.Layer FIRE = layer("fire_fade");
    private static SingleQuadParticle.Layer solidPortal() {
        var pipeline = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.PARTICLE_SNIPPET)
            .withLocation(Naturality.id("pipeline/portal_ready"))
            .withVertexShader(Naturality.id("core/portal_opening"))
            .withFragmentShader(Naturality.id("core/portal_opening"))
            .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, true))
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT)).build());
        // Finished surfaces belong in the depth-writing opaque pass. Otherwise an
        // opaque-looking translucent portal can paint over foreground glow rays.
        return new SingleQuadParticle.Layer(false, naturality.client.AtlasLocations.BLOCKS, pipeline, null);
    }
    private static SingleQuadParticle.Layer layer(String name) {
        var effectBuilder = RenderPipeline.builder().withVertexShader(Naturality.id("core/" + (name.equals("fire_fade") ? name : "portal_opening")))
            .withFragmentShader(Naturality.id("core/" + name));
        if (name.equals("fire_fade")) effectBuilder.withBindGroupLayout(naturality.client.fire.ProceduralFire.LAYOUT);
        var effect = effectBuilder.buildSnippet();
        var pipeline = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.PARTICLE_SNIPPET, effect)
            .withLocation(Naturality.id("pipeline/" + name))
            .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT)).build());
        var oit = RenderPipelines.register(OitPipelineSet.builder("naturality_" + name,
            RenderPipeline.builder(RenderPipelines.OIT_PARTICLE_SNIPPET, effect)).build());
        return new SingleQuadParticle.Layer(true, naturality.client.AtlasLocations.BLOCKS, pipeline, oit);
    }
    public static void initialize() { }
}
