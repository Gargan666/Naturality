package naturality.client.mixin;

import net.minecraft.client.model.object.equipment.ElytraModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ElytraModel.class)
public abstract class ElytraCapeMotionMixin {
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
