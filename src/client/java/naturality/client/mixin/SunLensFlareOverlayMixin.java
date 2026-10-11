package naturality.client.mixin;

import naturality.client.sky.SunLensFlare;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class SunLensFlareOverlayMixin {
    @Inject(method = "render", at = @At("HEAD"))
    private void naturality$resetFlare(CallbackInfo ci) {
        SunLensFlare.beginFrame();
    }

    @Inject(method = "render", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/gui/render/GuiRenderer;render()V"))
    private void naturality$cameraFlare(CallbackInfo ci) {
        SunLensFlare.renderOverlay();
    }
}
