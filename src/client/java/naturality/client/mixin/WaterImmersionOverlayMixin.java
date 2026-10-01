package naturality.client.mixin;

import naturality.client.fluid.WaterImmersionOverlay;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class WaterImmersionOverlayMixin {
    @Inject(method = "renderLevel", at = @At("TAIL"))
    private void naturality$immersionOverlay(CallbackInfo ci) {
        WaterImmersionOverlay.render();
    }
}
