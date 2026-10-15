package naturality.client.mixin;

import naturality.client.entity.CreeperTintState;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntityRenderer.class)
public abstract class CreeperModelTintMixin {
    @Inject(method = "getModelTint", at = @At("RETURN"), cancellable = true)
    private void naturality$tint(LivingEntityRenderState state, CallbackInfoReturnable<Integer> cir) {
        if (state instanceof CreeperTintState creeper)
            cir.setReturnValue(net.minecraft.util.ARGB.multiply(cir.getReturnValue(), creeper.naturality$foliageTint()));
    }
}
