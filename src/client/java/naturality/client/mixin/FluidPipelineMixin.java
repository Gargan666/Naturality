package naturality.client.mixin;

import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import naturality.client.fluid.ProceduralFluids;
import net.minecraft.client.renderer.RenderPipelines;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(RenderPipelines.class)
public abstract class FluidPipelineMixin {
    @Redirect(method = "<clinit>", at = @At(value = "INVOKE", target = "Lcom/mojang/renderpearl/api/pipeline/RenderPipeline$Builder;withFragmentShader(Ljava/lang/String;)Lcom/mojang/renderpearl/api/pipeline/RenderPipeline$Builder;"))
    private static RenderPipeline.Builder naturality$fluidLayout(RenderPipeline.Builder builder, String shader) {
        if (shader.equals("core/terrain")) {
            builder.withBindGroupLayout(naturality.client.weather.WindRendering.LAYOUT);
            builder.withBindGroupLayout(ProceduralFluids.LAYOUT);
            builder.withBindGroupLayout(naturality.client.fire.ProceduralFire.LAYOUT);
            builder.withBindGroupLayout(naturality.client.fluid.WaterVisuals.LAYOUT);
            builder.withBindGroupLayout(naturality.client.fluid.WaterVisuals.SCENE_LAYOUT);
        }
        return builder.withFragmentShader(shader);
    }
}
