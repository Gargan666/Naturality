package naturality.client.sky.shared.atmosphere.mixins;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.renderpearl.api.commands.RenderPass;
import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.world.level.MoonPhase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import naturality.client.rendering.fog.EndFogComposite;
import net.minecraft.client.renderer.state.level.SkyRenderState;
import org.joml.Vector3fc;
import org.joml.Vector4fc;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Feature integration hooks; each nested type retains its independent Mixin target and gate. */
public final class AtmosphereMixins {
    private AtmosphereMixins() {}

    @Mixin(SkyRenderer.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class CelestialSphereFadeMixin {
        @Unique private float naturality$sunSphereFade = 1.0F;
        @Unique private float naturality$moonSphereFade = 1.0F;

        @Inject(method = "renderSunMoonAndStars", at = @At("HEAD"))
        private void naturality$measureBodyLatitude(RenderPass pass, PoseStack pose, float sunAngle,
                float moonAngle, float starAngle, MoonPhase moonPhase, float rainBrightness,
                float starBrightness, CallbackInfo ci) {
            // SkyRenderer rotates the celestial sphere around X by these angles;
            // the resulting normalized vertical coordinate is cos(angle).
            naturality$sunSphereFade = sphereFade((float) Math.cos(sunAngle));
            naturality$moonSphereFade = sphereFade((float) Math.cos(moonAngle));
        }

        @ModifyArg(method = "renderSunMoonAndStars", at = @At(value = "INVOKE",
                target = "Lnet/minecraft/client/renderer/SkyRenderer;renderSun(Lcom/mojang/renderpearl/api/commands/RenderPass;FLcom/mojang/blaze3d/vertex/PoseStack;)V"), index = 1)
        private float naturality$fadeSun(float brightness) {
            return brightness * naturality$sunSphereFade;
        }

        @ModifyArg(method = "renderSunMoonAndStars", at = @At(value = "INVOKE",
                target = "Lnet/minecraft/client/renderer/SkyRenderer;renderMoon(Lcom/mojang/renderpearl/api/commands/RenderPass;Lnet/minecraft/world/level/MoonPhase;FLcom/mojang/blaze3d/vertex/PoseStack;)V"), index = 2)
        private float naturality$fadeMoon(float brightness) {
            return brightness * naturality$moonSphereFade;
        }

        @Inject(method = "renderSun", at = @At("HEAD"), cancellable = true)
        private void naturality$skipSunBelowSphereBoundary(RenderPass pass, float brightness, PoseStack pose,
                CallbackInfo ci) {
            if (naturality$sunSphereFade <= 0.0F) ci.cancel();
        }

        @Inject(method = "renderMoon", at = @At("HEAD"), cancellable = true)
        private void naturality$skipMoonBelowSphereBoundary(RenderPass pass, MoonPhase phase,
                float brightness, PoseStack pose, CallbackInfo ci) {
            if (naturality$moonSphereFade <= 0.0F) ci.cancel();
        }

        @Unique
        private static float sphereFade(float normalizedHeight) {
            // Start fading at the lower-60% boundary (y=+0.2), finish at the
            // lower-40% boundary (y=-0.2), and hide everything below it.
            float t = Math.clamp((normalizedHeight + 0.2F) / 0.4F, 0.0F, 1.0F);
            return t * t * (3.0F - 2.0F * t);
        }
    }

    @Mixin(SkyRenderer.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class OverworldFogAtmosphereMixin {
        @SuppressWarnings("null") @Shadow @Final private RenderTarget renderTarget;
        @SuppressWarnings("null") @Shadow @Final private com.mojang.renderpearl.api.buffers.GpuBuffer topSkyBuffer;
        @org.spongepowered.asm.mixin.Unique private naturality.client.sky.shared.atmosphere.@org.jspecify.annotations.Nullable SkyGradientRenderer naturality$gradient;
        @Inject(method = "render", at = @At("HEAD"))
        private void naturality$prepareGradient(GpuBufferSlice fog, SkyRenderState state, CallbackInfo ci) {
            if (naturality.client.api.rendering.ShaderPacks.active()) return;
            if (state.skybox != net.minecraft.world.level.dimension.DimensionType.Skybox.OVERWORLD) return;
            var gradient = naturality$gradient;
            if (gradient == null) { gradient = new naturality.client.sky.shared.atmosphere.SkyGradientRenderer(); naturality$gradient = gradient; }
            gradient.prepare(state.skyColor);
        }
        @Inject(method = "renderSkyDisc", at = @At("HEAD"), cancellable = true)
        private void naturality$gradient(RenderPass pass, Vector3fc color, CallbackInfo ci) {
            if (naturality.client.api.rendering.ShaderPacks.active()) return;
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
            if (naturality.client.api.rendering.ShaderPacks.active()) return;
            if (naturality$gradient != null) naturality$gradient.bindPalette(pass);
        }

        @org.spongepowered.asm.mixin.injection.ModifyArg(method = "renderSunriseAndSunset", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/systems/RenderSystem;getCompiledPipeline(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;)Lcom/mojang/renderpearl/api/pipeline/CompiledRenderPipeline;"), index = 0)
        private com.mojang.renderpearl.api.pipeline.RenderPipeline naturality$sunsetPalette(
                com.mojang.renderpearl.api.pipeline.RenderPipeline original) {
            if (naturality.client.api.rendering.ShaderPacks.active()) return original;
            return naturality.client.sky.overworld.sunset.SunsetPalette.PIPELINE;
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
}
