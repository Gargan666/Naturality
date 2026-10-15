package naturality.client.mixin;

import com.mojang.blaze3d.pipeline.PipelineCache;
import java.util.concurrent.Executor;
import naturality.client.render.ShaderPreparation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(PipelineCache.class)
public abstract class PipelinePreparationMixin {
    @ModifyArg(method = "get", at = @At(value = "INVOKE", target =
        "Lcom/mojang/renderpearl/api/device/GpuDevice;compilePipeline(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;Lcom/mojang/renderpearl/api/pipeline/ShaderSource;Ljava/util/concurrent/Executor;)Ljava/util/concurrent/CompletableFuture;"), index = 2)
    private Executor naturality$independentShaderPreparation(Executor terrainExecutor) {
        return ShaderPreparation.executor();
    }
}
