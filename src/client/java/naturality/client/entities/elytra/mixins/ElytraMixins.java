package naturality.client.entities.elytra.mixins;

import net.minecraft.client.model.object.equipment.ElytraModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.client.player.AbstractClientPlayer;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Feature integration hooks; each nested type retains its independent Mixin target and gate. */
public final class ElytraMixins {
    private ElytraMixins() {}

    @Mixin(ElytraModel.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class ElytraCapeMotionMixin {
        @Inject(method = "setupAnim", at = @At("TAIL"))
        private void naturality$foldedCapeMotion(HumanoidRenderState state, CallbackInfo ci) {
            if (!(state instanceof AvatarRenderState player) || state.isFallFlying) return;
            // Cape motion expressed in the wings' forward-facing model frame.
            // Preserve the folded wings' own resting angles and crouching offsets.
            float radians = (float)(Math.PI / 180.0);
            ((ElytraModel)(Object)this).root().rotateBy(new Quaternionf()
                    .rotateX((player.capeLean / 2.0F + player.capeFlap) * radians)
                    .rotateZ(player.capeLean2 / 2.0F * radians)
                    .rotateY(-player.capeLean2 / 2.0F * radians));
        }
    }

    @Mixin(AbstractClientPlayer.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class ElytraFovMixin {
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
}
