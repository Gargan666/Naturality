package naturality.client.effects.insanity.mixins;

import com.mojang.blaze3d.vertex.PoseStack;
import naturality.client.effects.insanity.Insanity;
import naturality.client.effects.insanity.Insanity.InsanityState;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.entity.EnderDragonRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import com.mojang.math.Axis;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import org.spongepowered.asm.mixin.Shadow;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import naturality.client.effects.insanity.Insanity.InsanitySoundSettings;
import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.Unique;

/** Feature integration hooks; each nested type retains its independent Mixin target and gate. */
public final class InsanityMixins {
    private InsanityMixins() {}

    @Mixin(EntityRenderDispatcher.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class InsanityDispatcherMixin {
        @Inject(method = "extractEntity", at = @At("RETURN"))
        private void naturality$extract(Entity entity, float partial, CallbackInfoReturnable<EntityRenderState> cir) {
            Insanity.extract(entity, cir.getReturnValue(), partial);
        }
        @Inject(method = "submit", at = @At("HEAD"))
        private void naturality$submit(EntityRenderState state, CameraRenderState camera, double x, double y, double z,
                PoseStack pose, SubmitNodeCollector collector, CallbackInfo ci) {
            if (!(state instanceof InsanityState insane)) return;
            var proxy = insane.naturality$insanity().hallucination;
            if (proxy != null) ((EntityRenderDispatcher)(Object)this).submit(proxy, camera, x, y, z, pose, collector);
        }
    }

    /** The dragon uses EntityRenderState and bypasses LivingEntityRenderer's body fade. */
    @Mixin(EnderDragonRenderer.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class InsanityDragonRendererMixin {
        @WrapOperation(method="submit", at=@At(value="INVOKE", target="Lnet/minecraft/client/renderer/SubmitNodeCollector;submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/resources/Identifier;III)V"))
        private <S extends @org.jspecify.annotations.NonNull Object> void naturality$body(SubmitNodeCollector collector, Model<? super S> model, S state, PoseStack pose,
                Identifier texture, int light, int overlay, int outline, Operation<Void> original) {
            float alpha=((InsanityState)state).naturality$insanity().alpha;
            if(alpha==1) original.call(collector,model,state,pose,texture,light,overlay,outline);
            else if(alpha>0) collector.submitModel(model,state,pose,RenderTypes.entityTranslucentCull(texture),light,overlay,ARGB.white(alpha),null,outline);
        }
        @WrapOperation(method="submit", at=@At(value="INVOKE", target="Lnet/minecraft/client/renderer/SubmitNodeCollector;submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/rendertype/RenderType;III)V"))
        private <S extends @org.jspecify.annotations.NonNull Object> void naturality$eyes(SubmitNodeCollector collector, Model<? super S> model, S state, PoseStack pose,
                RenderType type, int light, int overlay, int outline, Operation<Void> original) {
            if(((InsanityState)state).naturality$insanity().alpha==1)
                original.call(collector,model,state,pose,type,light,overlay,outline);
        }
    }

    @Mixin(LivingEntityRenderer.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class InsanityRendererMixin {
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

    @Mixin(SoundManager.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class InsanitySoundMixin {
        @ModifyVariable(method = "play", at = @At("HEAD"), argsOnly = true)
        private SoundInstance naturality$voice(SoundInstance sound) { return Insanity.sound(sound); }
    }

    @Mixin(AbstractSoundInstance.class)
    @org.jspecify.annotations.NullMarked
    public interface InsanitySoundSettingsMixin extends InsanitySoundSettings {
        @Accessor("volume") float naturality$volume();
        @Accessor("pitch") float naturality$pitch();
    }

    @Mixin(EntityRenderState.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class InsanityStateMixin implements InsanityState {
        @Unique private final Data naturality$data = new Data();
        public Data naturality$insanity() { return naturality$data; }
    }
}
