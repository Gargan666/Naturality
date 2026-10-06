package naturality.client.mixin;

import net.minecraft.client.player.AbstractClientPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractClientPlayer.class)
public abstract class ElytraFovMixin {
    @Inject(method = "getFieldOfViewModifier", at = @At("RETURN"), cancellable = true)
    private void naturality$flightFov(boolean firstPerson, float effectScale,
                                     CallbackInfoReturnable<Float> cir) {
        var player = (AbstractClientPlayer)(Object)this;
        if (!player.isFallFlying() || player.isScoping()) return;
        // Includes diving speed, with a gradual approach to the camera's 1.5x limit.
        // Camera.tickFov supplies tick easing and render interpolation, including landing.
        double speed = player.getDeltaMovement().length() * 20.0;
        float widening = (float)(0.5 * speed / (speed + 60.0));
        cir.setReturnValue(Math.min(1.5F, cir.getReturnValue() + effectScale * widening));
    }
}
