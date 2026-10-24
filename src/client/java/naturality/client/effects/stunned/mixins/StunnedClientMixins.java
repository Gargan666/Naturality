package naturality.client.effects.stunned.mixins;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import naturality.client.effects.stunned.StunnedClient;
import naturality.client.effects.stunned.StunnedVisuals;
import naturality.effects.stunned.Stunned;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.ParticlesRenderState;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec2;
import org.jspecify.annotations.NullMarked;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Local control suppression and effect-bound visual extraction. */
@NullMarked
public final class StunnedClientMixins {
    private StunnedClientMixins() {}

    @Mixin(EntityRenderState.class)
    public static abstract class VisualState implements StunnedVisuals.RenderState {
        @Unique private boolean naturality$stunned;

        @Override public boolean naturality$stunned() { return naturality$stunned; }
        @Override public void naturality$setStunned(boolean stunned) { naturality$stunned = stunned; }
    }

    @Mixin(EntityRenderer.class)
    public static abstract class ExtractVisualState {
        @Inject(method = "extractRenderState", at = @At("TAIL"))
        private void naturality$stunnedState(Entity entity, EntityRenderState state, float partialTick, CallbackInfo ci) {
            ((StunnedVisuals.RenderState)state).naturality$setStunned(Stunned.active(entity));
        }
    }

    @Mixin(Model.class)
    public static abstract class HeadAnimation implements StunnedVisuals.AnimatedModel {
        @Unique private final StunnedVisuals.HeadPose naturality$headPose = new StunnedVisuals.HeadPose();

        @Override public StunnedVisuals.HeadPose naturality$stunnedHeadPose() { return naturality$headPose; }

        @Inject(method = "setupAnim", at = @At("HEAD"))
        private void naturality$beginPose(Object state, CallbackInfo ci) { naturality$headPose.begin(state); }

        @Inject(method = "renderToBuffer", at = @At("HEAD"))
        private void naturality$wobbleHead(CallbackInfo ci) {
            // Apply after subclass animation, including deferred models and armor/glint passes.
            naturality$headPose.apply((Model<?>)(Object)this);
        }
    }

    @Mixin(LivingEntityRenderer.class)
    public static abstract class HeadLayers {
        @WrapOperation(method = "submit", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/model/EntityModel;setupAnim(Ljava/lang/Object;)V"))
        private void naturality$wobbleLayerPose(EntityModel<?> model, Object state, Operation<Void> original) {
            original.call(model, state);
            ((StunnedVisuals.AnimatedModel)model).naturality$stunnedHeadPose().apply(model);
        }
    }

    @Mixin(KeyboardInput.class)
    public static abstract class MovementInput extends ClientInput {
        @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
        private void naturality$emptyInput(CallbackInfo ci) {
            if (StunnedClient.active()) {
                keyPresses = Input.EMPTY;
                moveVector = Vec2.ZERO;
                ci.cancel();
            }
        }
    }

    @Mixin(LocalPlayer.class)
    public static abstract class PlayerInput {
        @Inject(method = "applyInput", at = @At("TAIL"))
        private void naturality$noAutoJump(CallbackInfo ci) {
            var player = (LocalPlayer)(Object)this;
            // Keep vanilla's xBob/yBob smoothing so held items settle after looking around.
            // Clear movement and auto-jump only after that visual update has run.
            if (Stunned.active(player)) Stunned.suppressActions(player);
        }
    }

    @Mixin(Minecraft.class)
    public static abstract class Actions {
        @Inject(method = "handleKeybinds", at = @At("HEAD"))
        private void naturality$noActions(CallbackInfo ci) {
            StunnedClient.suppressControls((Minecraft)(Object)this);
        }

        @Inject(method = {"startUseItem", "pickBlockOrEntity"}, at = @At("HEAD"), cancellable = true)
        private void naturality$noUse(CallbackInfo ci) {
            if (StunnedClient.active()) ci.cancel();
        }

        @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
        private void naturality$noAttack(CallbackInfoReturnable<Boolean> result) {
            if (StunnedClient.active()) result.setReturnValue(false);
        }

        @ModifyVariable(method = "continueAttack", at = @At("HEAD"), argsOnly = true)
        private boolean naturality$noHeldAttack(boolean down) {
            return down && !StunnedClient.active();
        }
    }

    @Mixin(ParticleEngine.class)
    public static abstract class Indicator {
        @Inject(method = "extract", at = @At("TAIL"))
        private void naturality$stunnedParticles(ParticlesRenderState state, Frustum frustum, Camera camera,
                float partialTick, CallbackInfo ci) {
            StunnedVisuals.extract(state, frustum, camera, partialTick);
        }
    }
}
