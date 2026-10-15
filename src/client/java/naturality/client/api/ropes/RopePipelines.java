package naturality.client.api.ropes;

import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.resources.Identifier;

/** Pipeline definitions shared by fishing lines and leads. */
public final class RopePipelines {
    static final RenderType LINE = RenderType.create("naturality_fishing_line",
        RenderSetup.builder(RenderPipelines.register(RenderPipeline.builder(RenderPipelines.LINES_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("naturality", "pipeline/fishing_line"))
            .withVertexShader(Identifier.fromNamespaceAndPath("naturality", "core/fishing_line"))
            .withFragmentShader(Identifier.fromNamespaceAndPath("naturality", "core/fishing_line"))
            .withColorTargetState(ColorTargetState.DEFAULT).build())).createRenderSetup());
    static final RenderType LIT_LINE = RenderType.create("naturality_lead",
        RenderSetup.builder(RenderPipelines.register(RenderPipeline.builder(RenderPipelines.LINES_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("naturality", "pipeline/lead"))
            .withVertexShader(Identifier.fromNamespaceAndPath("naturality", "core/fishing_line"))
            .withFragmentShader(Identifier.fromNamespaceAndPath("naturality", "core/lead"))
            .withBindGroupLayout(net.minecraft.client.renderer.BindGroupLayouts.SAMPLER2)
            .withColorTargetState(ColorTargetState.DEFAULT).build())).useLightmap().createRenderSetup());
    private RopePipelines() { }
}
