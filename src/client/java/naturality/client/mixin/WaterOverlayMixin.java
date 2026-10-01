package naturality.client.mixin;

import naturality.client.fluid.WaterImmersionOverlay;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The immersion overlay supplies the persistent biome tint, including spectators.
 * Do not stack vanilla's fixed-blue texture over it in first person. */
@Mixin(ScreenEffectRenderer.class)
public abstract class WaterOverlayMixin {
    @Inject(method = "submitWater", at = @At("HEAD"), cancellable = true)
    private static void naturality$useBiomeImmersionTint(CallbackInfo ci) {
        if (WaterImmersionOverlay.active()) ci.cancel();
    }
}
