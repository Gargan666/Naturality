package naturality.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import naturality.client.lighting.HardcoreDarkness;
import naturality.client.sky.EndFlashes;
import naturality.client.sky.MeteorShower;
import naturality.client.sky.RainbowRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.world.level.MoonPhase;
import net.minecraft.world.level.dimension.DimensionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SkyRenderer.class)
public abstract class SkyEventsMixin {
    @org.spongepowered.asm.mixin.Shadow @org.spongepowered.asm.mixin.Final
    private com.mojang.renderpearl.api.buffers.GpuBuffer endSkyBuffer;
    @org.spongepowered.asm.mixin.Shadow @org.spongepowered.asm.mixin.Final
    private net.minecraft.client.renderer.texture.AbstractTexture endSkyTexture;
    @org.spongepowered.asm.mixin.Shadow @org.spongepowered.asm.mixin.Final
    private com.mojang.renderpearl.api.buffers.GpuBuffer starBuffer;
    @org.spongepowered.asm.mixin.Shadow private int starIndexCount;

    @Inject(method = "renderEndSky", at = @At("HEAD"), cancellable = true)
    private void naturality$endSkyPlane(RenderPass pass, CallbackInfo ci) {
        naturality.client.sky.EndSkyRenderer.render(pass, endSkyBuffer, endSkyTexture);
        naturality.client.sky.EndSkyRenderer.renderStars(pass, starBuffer, starIndexCount);
        ci.cancel();
    }
    @Unique private @org.jspecify.annotations.Nullable MeteorShower naturality$meteors;
    @Unique private @org.jspecify.annotations.Nullable RainbowRenderer naturality$rainbow;
    @Unique private float naturality$rainbowSkyBlend;
    @Unique private float naturality$endDarkness = 1.0F;
    @Unique private boolean naturality$endEvent;
    @Unique private float naturality$flashSize = 1;
    @ModifyArg(method = "renderEndFlash", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/SkyRenderer;applyCelestialBodyTransform(Lcom/mojang/blaze3d/vertex/PoseStack;FF)Lorg/joml/Matrix4f;"), index = 2)
    private float naturality$flashScale(float scale) { return scale * naturality$flashSize; }
    @Inject(method = "render", at = @At("HEAD"))
    private void naturality$prepare(com.mojang.renderpearl.api.buffers.GpuBufferSlice fog,
            net.minecraft.client.renderer.state.level.SkyRenderState state, CallbackInfo ci) {
        EndFlashes.beginFrame();
        var client = Minecraft.getInstance();
        naturality$endEvent = state.skybox == DimensionType.Skybox.END && client.level != null
            && client.level.dimension().equals(net.minecraft.world.level.Level.END);
        if (naturality$endEvent) {
            float partial = client.getDeltaTracker().getGameTimeDeltaPartialTick(false);
            naturality$endDarkness = HardcoreDarkness.sky(client.level,
                client.gameRenderer.mainCamera(), partial);
        }
        // Only Overworld sky extraction supplies skyColor; fresh End frames leave it null.
        naturality$rainbowSkyBlend = 0;
        if (state.skybox != DimensionType.Skybox.OVERWORLD) return;
        var effect = naturality$meteors;
        if (effect == null) { effect = new MeteorShower(); naturality$meteors = effect; }
        effect.prepare();
        var rainbow = naturality$rainbow;
        if (rainbow == null) { rainbow = new RainbowRenderer(); naturality$rainbow = rainbow; }
        naturality$rainbowSkyBlend = RainbowRenderer.skyBlend(state.skyColor);
        rainbow.prepare();
    }
    @WrapOperation(method = "render", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/SkyRenderer;renderEndFlash(Lcom/mojang/renderpearl/api/commands/RenderPass;Lcom/mojang/blaze3d/vertex/PoseStack;FFF)V"))
    private void naturality$eventEndFlashes(SkyRenderer renderer, RenderPass pass, PoseStack pose,
            float intensity, float xAngle, float yAngle, Operation<Void> original) {
        if (!naturality$endEvent) {
            original.call(renderer, pass, pose, intensity, xAngle, yAngle);
            return;
        }
        float partial = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
        for (var flash : EndFlashes.visible(partial, naturality$endDarkness)) {
            naturality$flashSize = flash.size();
            try {
                original.call(renderer, pass, new PoseStack(), flash.intensity(), flash.xAngle(), flash.yAngle());
            } finally { naturality$flashSize = 1; }
            EndFlashes.flashDrawn();
        }
    }
    @Inject(method = "renderSunMoonAndStars", at = @At("TAIL"))
    private void naturality$events(RenderPass pass, PoseStack pose, float sunAngle, float moonAngle,
            float starAngle, MoonPhase moonPhase, float rainBrightness, float starBrightness, CallbackInfo ci) {
        var effect = naturality$meteors;
        if (effect == null) { effect = new MeteorShower(); naturality$meteors = effect; }
        effect.render(pass, rainBrightness);
        if (naturality$rainbow != null) naturality$rainbow.render(pass, sunAngle, naturality$rainbowSkyBlend);
    }
    @Inject(method = "close", at = @At("HEAD"))
    private void naturality$close(CallbackInfo ci) {
        if (naturality$meteors != null) { naturality$meteors.close(); naturality$meteors = null; }
        if (naturality$rainbow != null) { naturality$rainbow.close(); naturality$rainbow = null; }
    }
}

