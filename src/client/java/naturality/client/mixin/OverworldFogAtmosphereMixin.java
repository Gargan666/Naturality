package naturality.client.mixin;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.RenderPass;
import naturality.client.fog.EndFogComposite;
import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.client.renderer.state.level.SkyRenderState;
import org.joml.Vector3fc;
import org.joml.Vector4fc;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SkyRenderer.class)
public abstract class OverworldFogAtmosphereMixin {
    @SuppressWarnings("null") @Shadow @Final private RenderTarget renderTarget;
    @SuppressWarnings("null") @Shadow @Final private com.mojang.renderpearl.api.buffers.GpuBuffer topSkyBuffer;
    @org.spongepowered.asm.mixin.Unique private naturality.client.sky.@org.jspecify.annotations.Nullable SkyGradientRenderer naturality$gradient;
    @Inject(method = "render", at = @At("HEAD"))
    private void naturality$prepareGradient(GpuBufferSlice fog, SkyRenderState state, CallbackInfo ci) {
        if (state.skybox != net.minecraft.world.level.dimension.DimensionType.Skybox.OVERWORLD) return;
        var gradient = naturality$gradient;
        if (gradient == null) { gradient = new naturality.client.sky.SkyGradientRenderer(); naturality$gradient = gradient; }
        gradient.prepare(state.skyColor);
    }
    @Inject(method = "renderSkyDisc", at = @At("HEAD"), cancellable = true)
    private void naturality$gradient(RenderPass pass, Vector3fc color, CallbackInfo ci) {
        if (naturality$gradient == null) return;
        naturality$gradient.render(pass, topSkyBuffer, color);
        ci.cancel();
    }
    @Inject(method = "close", at = @At("HEAD"))
    private void naturality$closeGradient(CallbackInfo ci) {
        if (naturality$gradient != null) { naturality$gradient.close(); naturality$gradient = null; }
    }
    @Invoker("renderSkyDisc") protected abstract void naturality$skyDisc(RenderPass pass, Vector3fc color);
    @Invoker("renderSunriseAndSunset") protected abstract void naturality$sunset(RenderPass pass, PoseStack pose, float angle, Vector4fc color);
    @Invoker("renderDarkDisc") protected abstract void naturality$darkDisc(RenderPass pass);

    @Inject(method = "renderSunriseAndSunset", at = @At("HEAD"))
    private void naturality$sunsetGrid(RenderPass pass, PoseStack pose, float angle, Vector4fc color, CallbackInfo ci) {
        if (naturality$gradient != null) naturality$gradient.bindPalette(pass);
    }

    @org.spongepowered.asm.mixin.injection.ModifyArg(method = "renderSunriseAndSunset", at = @At(value = "INVOKE",
        target = "Lcom/mojang/blaze3d/systems/RenderSystem;getCompiledPipeline(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;)Lcom/mojang/renderpearl/api/pipeline/CompiledRenderPipeline;"), index = 0)
    private com.mojang.renderpearl.api.pipeline.RenderPipeline naturality$sunsetPalette(
            com.mojang.renderpearl.api.pipeline.RenderPipeline original) {
        return naturality.client.sky.SunsetPalette.PIPELINE;
    }

    // RETURN is after vanilla closes its render pass. Never copy or render into a
    // different target while the original sky pass is still open.
    @Inject(method = "render", at = @At("RETURN"))
    private void naturality$atmosphere(GpuBufferSlice fog, SkyRenderState state, CallbackInfo ci) {
        EndFogComposite.captureAtmosphere(renderTarget, pass -> {
            naturality$skyDisc(pass, state.skyColor);
            naturality$sunset(pass, new PoseStack(), state.sunAngle, state.sunriseAndSunsetColor);
            if (state.shouldRenderDarkDisc) naturality$darkDisc(pass);
        });
    }
}
