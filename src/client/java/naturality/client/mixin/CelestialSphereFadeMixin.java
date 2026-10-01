package naturality.client.mixin;

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

@Mixin(SkyRenderer.class)
public abstract class CelestialSphereFadeMixin {
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
