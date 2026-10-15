package naturality.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import naturality.client.insanity.InsanityState;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntityRenderer.class)
public abstract class InsanityRendererMixin {
    @Shadow public abstract Identifier getTextureLocation(LivingEntityRenderState state);
    @ModifyExpressionValue(method="submit", at=@At(value="INVOKE", target="Lnet/minecraft/client/renderer/entity/LivingEntityRenderer;getRenderType(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;ZZZ)Lnet/minecraft/client/renderer/rendertype/RenderType;"))
    private @org.jspecify.annotations.Nullable RenderType naturality$fade(@org.jspecify.annotations.Nullable RenderType original, @Local(argsOnly=true) LivingEntityRenderState state) {
        float alpha = ((InsanityState)state).naturality$insanity().alpha;
        if (alpha == 0) return null;
        return alpha < 1 && original != null ? RenderTypes.entityTranslucentCull(getTextureLocation(state)) : original;
    }
    @ModifyExpressionValue(method="submit", at=@At(value="INVOKE", target="Lnet/minecraft/client/renderer/entity/LivingEntityRenderer;getModelTint(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;)I"))
    private int naturality$alpha(int original, @Local(argsOnly=true) LivingEntityRenderState state) {
        float alpha = ((InsanityState)state).naturality$insanity().alpha;
        return alpha < 1 ? ARGB.multiply(original, Math.round(alpha * 255) << 24 | 0xFFFFFF) : original;
    }
    @Inject(method = "shouldRenderLayers", at = @At("HEAD"), cancellable = true)
    private void naturality$layers(LivingEntityRenderState state, CallbackInfoReturnable<Boolean> cir) {
        if (((InsanityState)state).naturality$insanity().alpha < 1) cir.setReturnValue(false);
    }
    @Inject(method = "setupRotations", at = @At("TAIL"))
    private void naturality$warp(LivingEntityRenderState state, PoseStack pose, float body, float scale, CallbackInfo ci) {
        float intensity = ((InsanityState)state).naturality$insanity().distortion;
        if (intensity == 0) return;
        float phase = state.ageInTicks * .09F;
        float pivot = state.boundingBoxHeight / scale / 2;
        pose.translate(0, pivot, 0);
        pose.rotateDegrees(Axis.ZP, (float)Math.sin(phase) * 25 * intensity);
        pose.rotateDegrees(Axis.XP, (float)Math.cos(phase * .7F) * 12 * intensity);
        float stretch = 1 + (float)Math.sin(phase * 1.3F) * .15F * intensity;
        pose.scale(1 / stretch, stretch, 1 / stretch);
        pose.translate(0, -pivot, 0);
    }
}
