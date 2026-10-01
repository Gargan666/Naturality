package naturality.client.mixin;

import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import java.util.Optional;
import naturality.client.cloud.CloudLayerRenderer;
import naturality.config.NaturalityConfig;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.renderer.CloudRenderer;
import net.minecraft.client.renderer.oit.OitRenderPassProvider;
import net.minecraft.client.renderer.oit.OitStage;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CloudRenderer.class)
public abstract class CloudRendererMixin {
    @Unique private final CloudLayerRenderer naturality$base = new CloudLayerRenderer();
    @Unique private final CloudLayerRenderer naturality$upper = new CloudLayerRenderer();
    @Unique private boolean naturality$active;
    @Unique private boolean naturality$upperFirst;

    @Inject(method = "apply(Ljava/util/Optional;Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)V", at = @At("TAIL"))
    private void naturality$texture(Optional<CloudRenderer.TextureData> data, ResourceManager manager, ProfilerFiller profiler, CallbackInfo ci) {
        naturality$base.setTexture(data.orElse(null));
        naturality$upper.setTexture(data.orElse(null));
    }

    @Inject(method = "prepare(ILnet/minecraft/client/CloudStatus;FILnet/minecraft/world/phys/Vec3;JF)V", at = @At("HEAD"), cancellable = true)
    private void naturality$prepare(int color, CloudStatus status, float height, int range, Vec3 camera, long time, float partial, CallbackInfo ci) {
        var config = NaturalityConfig.get().clouds;
        naturality$active = config.enabled;
        if (!naturality$active) return;
        naturality$base.prepare(color, status, height, range, camera, time, partial, config.base);
        naturality$upper.prepare(color, status, height, range, camera, time, partial, config.upper);
        naturality$upperFirst = Math.abs(height + config.upper.heightOffset + config.upper.thickness / 2.0 - camera.y)
            >= Math.abs(height + config.base.heightOffset + config.base.thickness / 2.0 - camera.y);
        ci.cancel();
    }

    @Inject(method = "render(Lnet/minecraft/client/CloudStatus;Lcom/mojang/renderpearl/api/commands/RenderPass;)V", at = @At("HEAD"), cancellable = true)
    private void naturality$render(CloudStatus status, RenderPass pass, CallbackInfo ci) {
        if (!naturality$active) return;
        // Both layers must populate depth before either contributes translucent color.
        naturality$base.renderDepth(pass);
        naturality$upper.renderDepth(pass);
        (naturality$upperFirst ? naturality$upper : naturality$base).render(status, pass);
        (naturality$upperFirst ? naturality$base : naturality$upper).render(status, pass);
        ci.cancel();
    }

    @Inject(method = "renderOit", at = @At("HEAD"), cancellable = true)
    private void naturality$renderOit(CloudStatus status, OitStage stage, GpuTextureView depth, OitRenderPassProvider.Parameters params, CallbackInfo ci) {
        if (!naturality$active) return;
        if (stage == OitStage.DEPTH_BOUNDS) {
            naturality$base.renderOitDepth(depth, params, true);
            naturality$upper.renderOitDepth(depth, params, false);
        }
        naturality$base.renderOit(status, stage, depth, params);
        naturality$upper.renderOit(status, stage, depth, params);
        ci.cancel();
    }

    @Inject(method = "endFrame", at = @At("TAIL"))
    private void naturality$endFrame(CallbackInfo ci) { naturality$base.endFrame(); naturality$upper.endFrame(); }

    @Inject(method = "markForRebuild", at = @At("TAIL"))
    private void naturality$rebuild(CallbackInfo ci) { naturality$base.markForRebuild(); naturality$upper.markForRebuild(); }

    @Inject(method = "close", at = @At("TAIL"))
    private void naturality$close(CallbackInfo ci) { naturality$base.close(); naturality$upper.close(); }
}
