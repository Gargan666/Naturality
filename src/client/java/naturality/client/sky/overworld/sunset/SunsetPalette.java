package naturality.client.sky.overworld.sunset;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.renderpearl.api.pipeline.*;
import naturality.Naturality;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.RenderPipelines;

@org.jspecify.annotations.NullMarked
public final class SunsetPalette {
    private SunsetPalette() {}
    public static final RenderPipeline PIPELINE = RenderPipelines.register(RenderPipeline.builder()
        .withLocation(Naturality.id("pipeline/sunset_palette"))
        .withVertexShader(Naturality.id("core/sunset_palette"))
        .withFragmentShader(Naturality.id("core/sunset_palette"))
        .withBindGroupLayout(BindGroupLayouts.GLOBALS)
        .withBindGroupLayout(BindGroupLayouts.PROJECTION)
        .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
        .withBindGroupLayout(BindGroupLayout.builder().withUniform("SkyPalette", UniformType.UNIFORM_BUFFER).build())
        .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
        .withPrimitiveTopology(PrimitiveTopology.TRIANGLE_FAN)
        .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
        .build());
}
