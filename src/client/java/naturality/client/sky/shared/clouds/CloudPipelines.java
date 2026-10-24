package naturality.client.sky.shared.clouds;

import com.mojang.renderpearl.api.pipeline.*;
import naturality.Naturality;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.oit.OitPipelineSet;

@org.jspecify.annotations.NullMarked
public final class CloudPipelines {
    public static final RenderPipeline DEPTH_ONLY = depth("depth", com.mojang.renderpearl.api.GpuFormat.RGBA8_UNORM);
    public static final RenderPipeline OIT_DEPTH_ONLY = depth("oit_depth", com.mojang.renderpearl.api.GpuFormat.RGBA32_FLOAT);
    private static final DepthStencilState DEPTH = new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false);
    public static final RenderPipeline VOLUME = classic("volume");
    public static final RenderPipeline FLAT = classic("flat");
    private static final BindGroupLayout WATER_ORDER = BindGroupLayout.builder()
        .withUniform("CloudNearestDepth", UniformType.COMBINED_IMAGE_SAMPLER)
        .withUniform("CloudWaterMask", UniformType.COMBINED_IMAGE_SAMPLER).build();
    public static final RenderPipeline BEHIND_WATER = waterOrder(false);
    public static final RenderPipeline IN_FRONT_OF_WATER = waterOrder(true);
    public static final OitPipelineSet OIT_VOLUME = oit("volume");
    public static final OitPipelineSet OIT_FLAT = oit("flat");

    private static RenderPipeline.Builder shaders(RenderPipeline.Builder builder) {
        return builder.withBindGroupLayout(naturality.client.ui.menus.previews.MenuSurface.MenuFog.LAYOUT).withVertexShader(Naturality.id("core/clouds"))
            .withFragmentShader(Naturality.id("core/clouds")).withCull(false);
    }

    private static RenderPipeline classic(String name) {
        return RenderPipelines.register(shaders(RenderPipeline.builder(RenderPipelines.CLOUDS_SNIPPET))
            .withLocation(Naturality.id("pipeline/cloud_" + name)).withDepthStencilState(DEPTH).build());
    }

    private static RenderPipeline depth(String name, com.mojang.renderpearl.api.GpuFormat format) {
        return RenderPipelines.register(shaders(RenderPipeline.builder(RenderPipelines.CLOUDS_SNIPPET))
            .withLocation(Naturality.id("pipeline/cloud_" + name))
            .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, true))
            .withColorTargetState(new ColorTargetState(java.util.Optional.empty(), format, 0)).build());
    }

    private static RenderPipeline waterOrder(boolean foreground) {
        var builder = shaders(RenderPipeline.builder(RenderPipelines.CLOUDS_SNIPPET))
            .withLocation(Naturality.id("pipeline/cloud_water_" + (foreground ? "front" : "back")))
            .withBindGroupLayout(WATER_ORDER).withShaderDefine("CLOUD_WATER_ORDER")
            .withDepthStencilState(DEPTH);
        if (foreground) builder.withShaderDefine("CLOUD_WATER_FRONT");
        return RenderPipelines.register(builder.build());
    }

    private static OitPipelineSet oit(String name) {
        // The shared cloud prepass already selected the nearest nonzero-alpha surface.
        return RenderPipelines.register(OitPipelineSet.builder("naturality_cloud_" + name,
            shaders(RenderPipeline.builder(RenderPipelines.OIT_CLOUDS_SNIPPET)))
            .withDepthBoundsModifier(builder -> builder.withDepthStencilState(DEPTH)).build());
    }

    public static void initialize() { }
    private CloudPipelines() { }
}
