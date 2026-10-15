package naturality.client.ui.camera.hands.mixins;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import naturality.client.ui.camera.airborne.AirborneTilt.AirborneCameraState;
import naturality.client.ui.camera.hands.ElasticMotion.ElasticHandPose;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(FirstPersonHandsAndItemsRenderer.class)
@org.jspecify.annotations.NullMarked
public abstract class ElasticHandsMixin implements naturality.client.ui.camera.hands.ElasticMotion.ElasticHandsState {
    @Unique private final ElasticHandPose[] naturality$motions = {
        new ElasticHandPose(), new ElasticHandPose(), new ElasticHandPose(),
        new ElasticHandPose(), new ElasticHandPose(), new ElasticHandPose()};
    @Unique private Matrix4f naturality$root = new Matrix4f();
    @Unique private Matrix4f naturality$inverseRoot = new Matrix4f();
    @Unique private double naturality$time;
    @Unique private float naturality$lift;
    @Unique private @org.jspecify.annotations.Nullable Object naturality$player, naturality$world;
    @Unique private boolean naturality$gliding;

    @WrapMethod(method = "submitHandsWithItems")
    private void naturality$frame(float partial, PoseStack pose, SubmitNodeCollector collector,
                                 PlayerRenderState playerState, FirstPersonHandsAndItemsRenderState state, Operation<Void> original) {
        var client = Minecraft.getInstance();
        var player = client.player;
        boolean gliding = player != null && (player.isFallFlying() || player.hasPose(net.minecraft.world.entity.Pose.FALL_FLYING));
        if (player != naturality$player || client.level != naturality$world || gliding != naturality$gliding) {
            for (var motion : naturality$motions) motion.reset();
            naturality$player = player; naturality$world = client.level; naturality$gliding = gliding;
        }
        naturality$time = player == null ? 0 : player.tickCount + partial;
        naturality$lift = client.gameRenderer.mainCamera() instanceof AirborneCameraState camera
            ? .055F * camera.naturality$fallMultiplier(partial) : 0;
        naturality$root.set(pose.last().pose());
        naturality$inverseRoot.set(naturality$root).invert();
        original.call(partial, pose, collector, playerState, state);
    }

    @Unique private void naturality$ease(PoseStack pose, int channel) {
        if (naturality$player == null) return;
        // Work relative to the incoming camera/bob transform: never delay player aiming.
        var relative = new Matrix4f(naturality$inverseRoot).mul(pose.last().pose());
        relative.m31(relative.m31() + naturality$lift);
        var eased = naturality$motions[channel].sample(relative, naturality$time);
        var replacement = new Matrix4f(naturality$root).mul(eased);
        pose.mulPose(new Matrix4f(pose.last().pose()).invert().mul(replacement));
    }

    @Override public void naturality$easeMeasuredItem(PoseStack pose, InteractionHand hand) {
        naturality$ease(pose, hand == InteractionHand.MAIN_HAND ? 4 : 5);
    }

    @WrapMethod(method = "renderPlayerHand")
    private void naturality$arm(PoseStack pose, SubmitNodeCollector collector, int light,
                               HumanoidArm arm, PlayerRenderState state, Operation<Void> original) {
        pose.pushPose();
        try { naturality$ease(pose, arm == HumanoidArm.RIGHT ? 0 : 1); original.call(pose, collector, light, arm, state); }
        finally { pose.popPose(); }
    }

    @WrapMethod(method = "renderMap")
    private void naturality$map(PoseStack pose, SubmitNodeCollector collector, int light, ItemStack stack,
                               boolean mainHand, FirstPersonHandsAndItemsRenderState state, Operation<Void> original) {
        pose.pushPose();
        try { naturality$ease(pose, mainHand ? 2 : 3); original.call(pose, collector, light, stack, mainHand, state); }
        finally { pose.popPose(); }
    }

    @WrapOperation(method = "submitArmWithItem", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/item/ItemStackRenderState;submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;III)V"))
    private void naturality$item(ItemStackRenderState item, PoseStack pose, SubmitNodeCollector collector,
        int light, int overlay, int outline, Operation<Void> original,
        PlayerRenderState player, FirstPersonHandsAndItemsRenderState hands, float partial, float pitch,
        InteractionHand hand, float attack, ItemStack stack, float height, PoseStack outer,
        SubmitNodeCollector outerCollector, int outerLight) {
        if (naturality.client.systems.fishing.FishingLineRendering.FirstPersonRodTip.measuring) {
            original.call(item, pose, collector, light, overlay, outline);
            return;
        }
        pose.pushPose();
        try { naturality$ease(pose, hand == InteractionHand.MAIN_HAND ? 4 : 5); original.call(item, pose, collector, light, overlay, outline); }
        finally { pose.popPose(); }
    }
}
