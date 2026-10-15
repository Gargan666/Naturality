package naturality.client.rendering.heatwaves;

import com.mojang.renderpearl.api.pipeline.*;
import java.util.*;
import naturality.Naturality;
import net.minecraft.client.renderer.*;

/** Shader definitions; nested groups preserve each consumer’s initialization boundary. */
@org.jspecify.annotations.NullMarked
public final class HeatWavePipeline {
    private HeatWavePipeline() { }
    public static final class Heat {
        public static final BindGroupLayout LAYOUT = BindGroupLayout.builder()
            .withUniform("HeatVolumes", UniformType.UNIFORM_BUFFER).build();
        public static final RenderPipeline PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.POST_PROCESSING_SNIPPET)
                .withLocation(Naturality.id("pipeline/heat_waves"))
                .withVertexShader(Naturality.id("core/heat_waves"))
                .withFragmentShader(Naturality.id("core/heat_waves"))
                .withBindGroupLayout(BindGroupLayouts.PROJECTION)
                .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
                .withBindGroupLayout(BindGroupLayouts.SAMPLER0_SAMPLER1)
                .withBindGroupLayout(LAYOUT).withDepthStencilState(Optional.empty())
                .withColorTargetState(ColorTargetState.DEFAULT).build());
    }
}
