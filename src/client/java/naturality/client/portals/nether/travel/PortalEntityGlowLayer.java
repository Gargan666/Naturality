package naturality.client.portals.nether.travel;
import naturality.client.portals.nether.glow.PortalGlowGeometry.PortalGlowAppearance;

import com.mojang.renderpearl.api.pipeline.*;
import naturality.Naturality;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.oit.OitPipelineSet;

@org.jspecify.annotations.NullMarked
public final class PortalEntityGlowLayer {
    private PortalEntityGlowLayer() {}
    public static final RenderPipeline.Snippet EFFECT = RenderPipeline.builder()
        .withVertexShader(Naturality.id("core/portal_opening"))
        .withFragmentShader(Naturality.id("core/portal_entity_glow")).withCull(false).buildSnippet();
    public static final RenderPipeline PIPELINE = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.PARTICLE_SNIPPET, EFFECT)
        .withLocation(Naturality.id("pipeline/portal_entity_glow"))
        .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
        .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT)).build());
    public static final OitPipelineSet OIT = RenderPipelines.register(OitPipelineSet.builder("naturality_entity_glow",
        RenderPipeline.builder(RenderPipelines.OIT_PARTICLE_SNIPPET, EFFECT)).build());
    public static final SingleQuadParticle.Layer SURFACE = new SingleQuadParticle.Layer(true, PortalGlowAppearance.PALETTE_TEXTURE, PIPELINE, OIT);
}
