package naturality.client.systems.fishing.mixins;

import com.mojang.blaze3d.vertex.PoseStack;
import naturality.client.systems.fishing.FishingLineRendering.FirstPersonRodTip;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import naturality.client.rendering.glint.mixins.GlintMixins.GlintItemLayersAccess;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.joml.Vector3f;
import naturality.client.systems.fishing.FishingBobberModel;
import naturality.client.systems.fishing.FishingLineRendering;
import net.minecraft.client.renderer.entity.FishingHookRenderer;
import net.minecraft.client.renderer.entity.state.FishingHookRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.joml.Quaternionf;
import naturality.client.systems.fishing.FishingLineRendering.FishingOwnerState;
import org.spongepowered.asm.mixin.Unique;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import org.spongepowered.asm.mixin.gen.Accessor;
import naturality.client.systems.fishing.FishingLineRendering.PlayerRodTips;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.HumanoidArm;
import org.spongepowered.asm.mixin.gen.Invoker;
import naturality.systems.villagerwork.framework.VillagerWork.VillagerWorkVisuals;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.properties.conditional.FishingRodCast;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemDisplayContext;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import naturality.client.systems.villagerwork.framework.VillagerVisualState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.entity.state.VillagerRenderState;

/** Feature integration hooks; each nested type retains its independent Mixin target and gate. */
public final class FishingMixins {
    private FishingMixins() {}

    @Mixin(GameRenderer.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class FirstPersonRodFrameMixin {
        @Shadow private void bobHurt(CameraRenderState camera, PoseStack pose) { throw new AssertionError(); }
        @Shadow private void bobView(CameraRenderState camera, PoseStack pose) { throw new AssertionError(); }
        @Inject(method = "renderLevel", at = @At("HEAD"))
        private void naturality$measureHand(CallbackInfo ci) {
            FirstPersonRodTip.worldTip = null;
            naturality.client.systems.fishing.FishingLineRendering.PlayerRodTips.clear();
            var client = Minecraft.getInstance();
            var localPlayer = client.player;
            if (localPlayer == null || (!localPlayer.getMainHandItem().is(net.minecraft.world.item.Items.FISHING_ROD)
                    && !localPlayer.getOffhandItem().is(net.minecraft.world.item.Items.FISHING_ROD))) return;
            var renderer = client.gameRenderer;
            var frame = renderer.gameRenderState();
            var player = frame.levelRenderState.playerRenderState;
            var camera = frame.levelRenderState.cameraRenderState;
            if (!frame.optionsRenderState.cameraType.isFirstPerson() || player.firstPersonHandsAndItems == null)
                return;
            FirstPersonRodTip.viewTip = null;
            FirstPersonRodTip.measuring = true;
            try {
                renderer.firstPersonHandsAndItemsRenderer.submitHandsWithItems(camera.cameraEntityPartialTicks,
                    new PoseStack(), new SubmitNodeStorage(), player, player.firstPersonHandsAndItems);
            } finally {
                FirstPersonRodTip.measuring = false;
            }
            var tip = FirstPersonRodTip.viewTip;
            if (tip != null) {
                PoseStack bob = new PoseStack();
                bobHurt(camera, bob);
                if (frame.optionsRenderState.bobView) bobView(camera, bob);
                tip.mulPosition(bob.last().pose());
                float hudScale = (float)(1.0 / Math.tan(Math.toRadians(camera.hudFov) / 2));
                float scale = hudScale / camera.projectionMatrix.m11();
                // Small screen-space correction toward the visible edge of the tip.
                tip.x = (tip.x - .025F) * scale;
                tip.y *= scale;
                FirstPersonRodTip.pixelSize *= scale;
                // World projection also contains bob. Undo it after converting
                // hand FOV to world FOV; the two transforms do not commute.
                tip.mulPosition(new org.joml.Matrix4f(bob.last().pose()).invert());
                tip.rotate(camera.orientation);
                FirstPersonRodTip.worldTip = renderer.mainCamera().position().add(new Vec3(tip));
            }
        }
    }

    @Mixin(FirstPersonHandsAndItemsRenderer.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class FirstPersonRodTipMixin {
        @WrapOperation(method = "submitArmWithItem", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/item/ItemStackRenderState;submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;III)V"))
        private void naturality$measure(ItemStackRenderState item, PoseStack pose, SubmitNodeCollector nodes,
                int light, int overlay, int outline, Operation<Void> original,
                PlayerRenderState player, FirstPersonHandsAndItemsRenderState hands, float partialTicks,
                float pitch, InteractionHand hand, float attack, ItemStack stack, float height,
                PoseStack outer, SubmitNodeCollector collector, int outerLight) {
            if (!FirstPersonRodTip.measuring) {
                original.call(item, pose, nodes, light, overlay, outline);
                return;
            }
            if (stack.is(Items.FISHING_ROD)) {
                if ((Object)this instanceof naturality.client.ui.camera.hands.ElasticMotion.ElasticHandsState elastic)
                    elastic.naturality$easeMeasuredItem(pose, hand);
                var layers = (GlintItemLayersAccess)item;
                if (layers.naturality$activeLayers() > 0) {
                    pose.pushPose();
                    ((RodItemTransformAccess)layers.naturality$layers()[0]).naturality$applyTransform(pose.last());
                    FirstPersonRodTip.viewTip = new Vector3f(13.5F / 16, 15.5F / 16, 0.5F)
                        .mulPosition(pose.last().pose());
                    Vector3f adjacentPixel = new Vector3f(12.5F / 16, 15.5F / 16, 0.5F)
                        .mulPosition(pose.last().pose());
                    Vector3f tip = FirstPersonRodTip.viewTip;
                    float screenX = adjacentPixel.x / adjacentPixel.z - tip.x / tip.z;
                    float screenY = adjacentPixel.y / adjacentPixel.z - tip.y / tip.z;
                    FirstPersonRodTip.pixelSize = Math.abs(tip.z)
                        * (float)Math.sqrt(screenX * screenX + screenY * screenY);
                    pose.popPose();
                }
            }
        }
    }

    @Mixin(FishingHookRenderer.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class FishingHookModelMixin {
        @Inject(method = "extractRenderState", at = @At("TAIL"))
        private void naturality$owner(net.minecraft.world.entity.projectile.FishingHook hook,
                FishingHookRenderState state, float partialTicks, CallbackInfo ci) {
            var owner = hook.getPlayerOwner();
            ((naturality.client.systems.fishing.FishingLineRendering.FishingOwnerState)state).naturality$setOwnerId(owner == null ? -1 : owner.getId());
            ((naturality.client.systems.fishing.FishingLineRendering.FishingOwnerState)state).naturality$setGlint(FishingBobberModel.rodGlint(owner));
        }
        @WrapOperation(method = "submit", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitCustomGeometry(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/rendertype/RenderType;Lnet/minecraft/client/renderer/SubmitNodeCollector$CustomGeometryRenderer;)V",
            ordinal = 0))
        private void naturality$customBobber(SubmitNodeCollector collector, PoseStack pose, RenderType type,
                SubmitNodeCollector.CustomGeometryRenderer geometry, Operation<Void> original,
                FishingHookRenderState state, PoseStack outerPose, SubmitNodeCollector nodes, CameraRenderState camera) {
            // Vanilla's flat quad has already been scaled and billboards toward the camera.
            // Undo those transforms so the model keeps its authored world orientation.
            outerPose.pushPose();
            outerPose.rotate(new Quaternionf(camera.orientation).conjugate());
            outerPose.scale(2, 2, 2);
            FishingBobberModel.submit(outerPose, collector, state.lightCoords,
                ((naturality.client.systems.fishing.FishingLineRendering.FishingOwnerState)state).naturality$glint());
            outerPose.popPose();
        }

        @WrapOperation(method = "submit", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitCustomGeometry(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/rendertype/RenderType;Lnet/minecraft/client/renderer/SubmitNodeCollector$CustomGeometryRenderer;)V",
            ordinal = 1))
        private void naturality$lineAtStem(SubmitNodeCollector collector, PoseStack pose, RenderType type,
                SubmitNodeCollector.CustomGeometryRenderer geometry, Operation<Void> original,
                FishingHookRenderState state, PoseStack outerPose, SubmitNodeCollector nodes, CameraRenderState camera) {
            Vec3 offset = state.lineOriginOffset.add(0,
                FishingLineRendering.VANILLA_ANCHOR_Y - FishingLineRendering.ANCHOR_Y, 0);
            Vec3 fallback = offset;
            int ownerId = ((naturality.client.systems.fishing.FishingLineRendering.FishingOwnerState)state).naturality$ownerId();
            Vec3 origin = new Vec3(state.x, state.y + FishingLineRendering.ANCHOR_Y, state.z);
            // Resolve after all entity item layers have submitted their poses, so
            // hook/player submission order cannot produce an old-frame endpoint.
            FishingLineRendering.submit(pose, collector, "player-fishing:" + ownerId, origin, () -> {
                var client = Minecraft.getInstance();
                Vec3 tip = client.player != null && ownerId == client.player.getId()
                        && client.options.getCameraType().isFirstPerson()
                    ? naturality.client.systems.fishing.FishingLineRendering.FirstPersonRodTip.worldTip
                    : naturality.client.systems.fishing.FishingLineRendering.PlayerRodTips.tips.get(ownerId);
                return tip == null ? fallback : tip.subtract(origin);
            });
        }
    }

    @Mixin(FishingHookRenderState.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class FishingOwnerStateMixin implements FishingOwnerState {
        @Unique private int naturality$ownerId = -1;
        @Unique private boolean naturality$glint;
        public boolean naturality$glint() { return naturality$glint; }
        public void naturality$setGlint(boolean value) { naturality$glint = value; }
        public int naturality$ownerId() { return naturality$ownerId; }
        public void naturality$setOwnerId(int id) { naturality$ownerId = id; }
    }

    @Mixin(LivingEntityRenderer.class)
    @org.jspecify.annotations.NullMarked
    public interface LivingEntityItemResolverAccess {
        @Accessor("itemModelResolver") ItemModelResolver naturality$itemModelResolver();
    }

    @Mixin(ItemInHandLayer.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class PlayerRodItemPoseMixin {
        @WrapOperation(method = "submitArmWithItem", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/item/ItemStackRenderState;submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;III)V"))
        private void naturality$captureRod(ItemStackRenderState item, PoseStack pose, SubmitNodeCollector nodes,
                int light, int overlay, int outline, Operation<Void> original,
                ArmedEntityRenderState state, ItemStackRenderState outerItem, ItemStack stack,
                HumanoidArm arm, PoseStack outerPose, SubmitNodeCollector collector, int outerLight) {
            if (state instanceof AvatarRenderState player && stack.is(Items.FISHING_ROD)) {
                var root = PlayerRodTips.roots.get(player);
                var layers = (GlintItemLayersAccess)item;
                if (root != null && layers.naturality$activeLayers() > 0) {
                    pose.pushPose();
                    ((RodItemTransformAccess)layers.naturality$layers()[0]).naturality$applyTransform(pose.last());
                    Vector3f tip = new Vector3f(13.5F / 16, 15.5F / 16, 0.5F)
                        .mulPosition(pose.last().pose()).mulPosition(root);
                    PlayerRodTips.tips.put(player.id, new Vec3(player.x + tip.x, player.y + tip.y, player.z + tip.z));
                    pose.popPose();
                }
            }
            original.call(item, pose, nodes, light, overlay, outline);
        }
    }

    @Mixin(ItemStackRenderState.LayerRenderState.class)
    @org.jspecify.annotations.NullMarked
    public interface RodItemTransformAccess {
        @Invoker("applyTransform") void naturality$applyTransform(PoseStack.Pose pose);
    }

    @Mixin(FishingRodCast.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class VillagerFishingRodCastMixin {
        @Inject(method = "get", at = @At("HEAD"), cancellable = true)
        private void naturality$castModel(ItemStack stack, @Nullable ClientLevel level,
                @Nullable LivingEntity owner, int seed, ItemDisplayContext context,
                CallbackInfoReturnable<Boolean> cir) {
            if (owner instanceof Villager villager && villager.getMainHandItem() == stack)
                cir.setReturnValue(((VillagerWorkVisuals)villager).naturality$castTarget() != null);
        }
    }

    @Mixin(LivingEntityRenderer.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class VillagerRodPoseCaptureMixin {
        @Inject(method = "submit", at = @At("HEAD"))
        private void naturality$beginCastRender(LivingEntityRenderState state, PoseStack poseStack,
                SubmitNodeCollector collector, CameraRenderState camera, CallbackInfo ci) {
            if (state instanceof VillagerRenderState villager) {
                var visual = (VillagerVisualState)villager;
                visual.naturality$basePoseInverse().set(poseStack.last().pose()).invert();
                visual.naturality$setRodTipValid(false);
            }
            if (state instanceof net.minecraft.client.renderer.entity.state.AvatarRenderState player)
                naturality.client.systems.fishing.FishingLineRendering.PlayerRodTips.roots.put(player,
                    new org.joml.Matrix4f(poseStack.last().pose()).invert());
        }
    }
}
