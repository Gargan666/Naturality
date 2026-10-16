package naturality.client.systems.swimming.mixins;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import naturality.client.systems.rotation.PlayerRotationRendering;
import naturality.client.systems.swimming.PlayerSwimmingClient;
import naturality.client.systems.swimming.WaterSurfaceRendering;
import naturality.systems.swimming.PlayerSwimming;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.gui.contextualbar.ContextualBar;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.model.QuadrupedModel;
import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

public final class SwimmingMixins {
    private SwimmingMixins() { }

    @Mixin(LocalPlayer.class)
    public static abstract class PlayerSwimmingInputMixin {
        @WrapOperation(method = "aiStep", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/ClientInput;tick()V"))
        private void naturality$input(ClientInput input, Operation<Void> original) {
            original.call(input);
            PlayerSwimmingClient.input((LocalPlayer)(Object)this);
        }
        @Inject(method = {"isShiftKeyDown", "isCrouching", "isMovingSlowly"}, at = @At("HEAD"), cancellable = true)
        private void naturality$noSneaking(CallbackInfoReturnable<Boolean> cir) {
            if (PlayerSwimming.waterMovement((LocalPlayer)(Object)this)) cir.setReturnValue(false);
        }
    }

    @Mixin(AvatarRenderState.class)
    public static abstract class PlayerSwimmingRenderStateMixin implements PlayerSwimmingClient.RenderState {
        @Unique private boolean naturality$swimming;
        @Unique private float naturality$gentleStroke;
        @Unique private float naturality$strokePhase;
        public boolean naturality$physicalSwimming() { return naturality$swimming; }
        public void naturality$physicalSwimming(boolean value) { naturality$swimming = value; }
        public float naturality$gentleStroke() { return naturality$gentleStroke; }
        public void naturality$gentleStroke(float value) { naturality$gentleStroke = value; }
        public float naturality$strokePhase() { return naturality$strokePhase; }
        public void naturality$strokePhase(float value) { naturality$strokePhase = value; }
    }

    @Mixin(AvatarRenderer.class)
    public static abstract class PlayerSwimmingRendererMixin {
        @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V", at = @At("TAIL"))
        private void naturality$extract(Avatar entity, AvatarRenderState state, float partial, CallbackInfo ci) {
            boolean swimming = entity instanceof Player player && PlayerSwimming.active(player);
            ((PlayerSwimmingClient.RenderState)state).naturality$physicalSwimming(swimming);
            ((PlayerSwimmingClient.RenderState)state).naturality$gentleStroke(entity instanceof Player player ? PlayerSwimming.gentleStroke(player, partial) : 0);
            ((PlayerSwimmingClient.RenderState)state).naturality$strokePhase(entity instanceof Player player ? PlayerSwimming.strokePhase(player, partial) : 0);
            if (swimming) state.isVisuallySwimming = true;
        }
        // The zero-angle entry pose must also bypass vanilla's extra swimming tilt/translation.
        @Inject(method = "setupRotations(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;FF)V", at = @At("HEAD"), cancellable = true)
        private void naturality$rotate(AvatarRenderState state, PoseStack poses, float yaw, float scale, CallbackInfo ci) {
            if (((PlayerSwimmingClient.RenderState)state).naturality$physicalSwimming()
                    || ((WaterSurfaceRendering.State)state).naturality$surfaceFloating()) {
                PlayerRotationRendering.rotate(state, poses, yaw, scale);
                ci.cancel();
            }
        }
    }

    @Mixin(Hud.class)
    public static abstract class PlayerSwimmingHudMixin {
        @WrapOperation(method = "extractHotbarAndDecorations", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/contextualbar/ContextualBar;extractBackground(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V"))
        private void naturality$background(ContextualBar bar, GuiGraphicsExtractor graphics, DeltaTracker delta, Operation<Void> original) {
            if (PlayerSwimmingClient.showBar()) PlayerSwimmingClient.bar(graphics);
            else original.call(bar, graphics, delta);
        }
        @WrapOperation(method = "extractHotbarAndDecorations", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/contextualbar/ContextualBar;extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V"))
        private void naturality$foreground(ContextualBar bar, GuiGraphicsExtractor graphics, DeltaTracker delta, Operation<Void> original) {
            if (!PlayerSwimmingClient.showBar()) original.call(bar, graphics, delta);
        }
    }

    @Mixin(HumanoidModel.class)
    public static abstract class PlayerSwimmingHeadMixin {
        @WrapOperation(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;)V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/model/HumanoidModel;setupSwimAnimation(Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;FF)V"))
        private void naturality$strokeClock(HumanoidModel<?> model, HumanoidRenderState state, float animation, float amount, Operation<Void> original) {
            float leftLeg = model.leftLeg.xRot, rightLeg = model.rightLeg.xRot;
            if (state instanceof PlayerSwimmingClient.RenderState swimming)
                animation = swimming.naturality$strokePhase() * (26F / (2F * (float)Math.PI));
            original.call(model, state, animation, amount);
            if (state instanceof PlayerSwimmingClient.RenderState swimming)
                PlayerSwimmingClient.strokeLegs(model, swimming.naturality$strokePhase(), amount, leftLeg, rightLeg);
        }
        @Inject(method = "setupSwimAnimation", at = @At("RETURN"))
        private void naturality$physicalHead(HumanoidRenderState state, float animation, float amount, CallbackInfo ci) {
            PlayerSwimmingClient.animate((HumanoidModel<?>)(Object)this, state);
        }
    }

    @Mixin(LivingEntityRenderState.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class WaterSurfaceRenderStateMixin implements WaterSurfaceRendering.State {
        @Unique private Vec3 naturality$surfaceLean = Vec3.ZERO;
        @Unique private float naturality$surfaceEyes;
        @Unique private float naturality$surfaceStroke;
        @Unique private boolean naturality$surfaceFloating;
        public Vec3 naturality$surfaceLean() { return naturality$surfaceLean; }
        public void naturality$surfaceLean(Vec3 lean) { naturality$surfaceLean = lean; }
        public float naturality$surfaceEyes() { return naturality$surfaceEyes; }
        public void naturality$surfaceEyes(float height) { naturality$surfaceEyes = height; }
        public float naturality$surfaceStroke() { return naturality$surfaceStroke; }
        public void naturality$surfaceStroke(float stroke) { naturality$surfaceStroke = stroke; }
        public boolean naturality$surfaceFloating() { return naturality$surfaceFloating; }
        public void naturality$surfaceFloating(boolean value) { naturality$surfaceFloating = value; }
    }

    @Mixin(LivingEntityRenderer.class)
    public static abstract class WaterSurfaceRendererMixin {
        @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V", at = @At("TAIL"))
        private void naturality$extractSurface(LivingEntity entity, LivingEntityRenderState state, float partial, CallbackInfo ci) {
            WaterSurfaceRendering.extract(entity, state, partial, ((LivingEntityRenderer<?, ?, ?>)(Object)this).getModel());
        }
        // Before the virtual call: applies once even to mob renderers overriding setupRotations.
        @Inject(method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/LivingEntityRenderer;setupRotations(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;FF)V"))
        private void naturality$surfaceRotation(LivingEntityRenderState state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera, CallbackInfo ci) {
            WaterSurfaceRendering.rotate(state, pose);
        }
    }

    @Mixin(HumanoidModel.class)
    public static abstract class WaterSurfaceHumanoidMixin {
        @Shadow protected abstract void setupSwimAnimation(HumanoidRenderState state, float animation, float amount);
        @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;)V", at = @At("TAIL"))
        private void naturality$surfaceStrokes(HumanoidRenderState state, CallbackInfo ci) {
            if (state instanceof AvatarRenderState) {
                var player = (PlayerSwimmingClient.RenderState)state;
                if (player.naturality$gentleStroke() > 0.001)
                    PlayerSwimmingClient.gentleSwim((HumanoidModel<?>)(Object)this, state, player.naturality$strokePhase(), player.naturality$gentleStroke());
                return;
            }
            float stroke = ((WaterSurfaceRendering.State)state).naturality$surfaceStroke();
            if (stroke <= 0.001) return;
            var model = (HumanoidModel<?>)(Object)this;
            float head = model.head.xRot;
            setupSwimAnimation(state, state.walkAnimationPos, stroke);
            model.head.xRot = head;
        }
    }

    @Mixin(QuadrupedModel.class)
    public static abstract class WaterSurfaceQuadrupedMixin {
        @Shadow @Final protected ModelPart rightHindLeg;
        @Shadow @Final protected ModelPart leftHindLeg;
        @Shadow @Final protected ModelPart rightFrontLeg;
        @Shadow @Final protected ModelPart leftFrontLeg;
        @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;)V", at = @At("TAIL"))
        private void naturality$surfaceKicks(LivingEntityRenderState state, CallbackInfo ci) {
            WaterSurfaceRendering.leg(rightHindLeg, state, 0);
            WaterSurfaceRendering.leg(leftHindLeg, state, (float)Math.PI);
            WaterSurfaceRendering.leg(rightFrontLeg, state, (float)Math.PI);
            WaterSurfaceRendering.leg(leftFrontLeg, state, 0);
        }
    }
}
