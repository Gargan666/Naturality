package naturality.client.mixin;

import naturality.client.menu.MenuPreview;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FogRenderer.class)
public abstract class MenuPreviewFogMixin {
    @Inject(method = "setupFog", at = @At("RETURN"))
    private void naturality$regionDistance(Camera camera, int distance, DeltaTracker delta, float darken,
                                          ClientLevel level, CallbackInfoReturnable<FogData> ci) {
        if (!MenuPreview.active()) return;
        // Environmental/weather fog stays native; distance fog is centered on the
        // miniature instead of fogging its center from the external camera.
        var fog = ci.getReturnValue();
        fog.renderDistanceStart = 1.0e6F;
        fog.renderDistanceEnd = 1.0e6F + 1;
    }
}
