package naturality.client.rendering.glint;

import com.mojang.blaze3d.vertex.*;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.pipeline.*;
import java.util.*;
import naturality.Naturality;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.RenderPipelines;

/** Shader definitions with separate initialization groups for their consumers. */
@org.jspecify.annotations.NullMarked
public final class GlintPipelines {
    private GlintPipelines() { }
    public static final class World {
        public static final RenderPipeline MASK = RenderPipelines.register(RenderPipeline.builder()
            .withLocation(Naturality.id("pipeline/glint_contour_mask"))
            .withVertexShader(Naturality.id("core/glint_contour_mask"))
            .withFragmentShader(Naturality.id("core/glint_contour_mask"))
            .withBindGroupLayout(BindGroupLayouts.PROJECTION)
            .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
            .withBindGroupLayout(BindGroupLayouts.GLOBALS)
            .withBindGroupLayout(BindGroupLayouts.FOG)
            .withBindGroupLayout(BindGroupLayouts.SAMPLER0_SAMPLER1)
            .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX)
            .withPrimitiveTopology(PrimitiveTopology.QUADS).withCull(false)
            .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, true))
            .withColorTargetState(new ColorTargetState(Optional.empty(), GpuFormat.RGBA32_FLOAT, 15)).build());
        private static RenderPipeline expansion(boolean gui) {
            var builder = RenderPipeline.builder(RenderPipelines.POST_PROCESSING_SNIPPET)
                .withLocation(Naturality.id(gui ? "pipeline/glint_contour_gui" : "pipeline/glint_contour"))
                .withVertexShader("core/screenquad")
                .withFragmentShader(Naturality.id("core/glint_contour"))
                .withBindGroupLayout(BindGroupLayouts.PROJECTION)
                .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
                .withBindGroupLayout(BindGroupLayouts.SAMPLER0_SAMPLER1_SAMPLER2)
                .withDepthStencilState(Optional.empty());
            builder.withBindGroupLayout(BindGroupLayouts.GLOBALS)
                .withColorTargetState(new ColorTargetState(gui
                    ? new BlendFunction(BlendFactor.ONE, BlendFactor.ONE_MINUS_SRC_ALPHA, BlendFactor.ONE, BlendFactor.ONE_MINUS_SRC_ALPHA)
                    : BlendFunction.GLINT));
            return RenderPipelines.register(builder.build());
        }
        public static final RenderPipeline EDGE = expansion(false);
        public static final RenderPipeline GUI_EDGE = expansion(true);
    }
    public static final class Inventory {
        public static final RenderPipeline PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.GUI_TEXTURED_SNIPPET)
                .withLocation(Naturality.id("pipeline/inventory_glint_contour"))
                .withVertexShader(Naturality.id("core/inventory_glint_contour"))
                .withFragmentShader(Naturality.id("core/inventory_glint_contour"))
                .withBindGroupLayout(BindGroupLayouts.SAMPLER1)
                .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT_PREMULTIPLIED_ALPHA)).build());

    }
}
