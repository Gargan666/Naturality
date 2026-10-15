package naturality.client.rendering.fog;

import com.mojang.renderpearl.api.pipeline.*;
import naturality.Naturality;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.RenderPipelines;
import java.util.Optional;

/** Shader definitions with separate initialization groups for their consumers. */
@org.jspecify.annotations.NullMarked
public final class FogPipeline {
    private FogPipeline() { }
    public static final class Composite {
        public static final RenderPipeline PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.POST_PROCESSING_SNIPPET)
                .withLocation(Naturality.id("pipeline/end_fog"))
                .withVertexShader("core/screenquad")
                .withFragmentShader(Naturality.id("core/end_fog"))
                .withBindGroupLayout(BindGroupLayouts.PROJECTION)
                .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
                .withBindGroupLayout(BindGroupLayouts.FOG)
                .withBindGroupLayout(BindGroupLayouts.SAMPLER0_SAMPLER1)
                .withBindGroupLayout(naturality.client.fluids.water.WaterVisuals.LAYOUT)
                .withBindGroupLayout(naturality.client.fluids.water.WaterVisuals.SCENE_LAYOUT)
                .withDepthStencilState(Optional.empty())
                .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                .build());
    }
}
