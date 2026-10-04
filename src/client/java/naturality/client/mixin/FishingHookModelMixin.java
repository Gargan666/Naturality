package naturality.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import naturality.client.villager.FishingBobberModel;
import naturality.client.villager.FishingLineRendering;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.FishingHookRenderer;
import net.minecraft.client.renderer.entity.state.FishingHookRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FishingHookRenderer.class)
public abstract class FishingHookModelMixin {
    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void naturality$owner(net.minecraft.world.entity.projectile.FishingHook hook,
            FishingHookRenderState state, float partialTicks, CallbackInfo ci) {
        var owner = hook.getPlayerOwner();
        ((naturality.client.villager.FishingOwnerState)state).naturality$setOwnerId(owner == null ? -1 : owner.getId());
        ((naturality.client.villager.FishingOwnerState)state).naturality$setGlint(FishingBobberModel.rodGlint(owner));
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
            ((naturality.client.villager.FishingOwnerState)state).naturality$glint());
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
        int ownerId = ((naturality.client.villager.FishingOwnerState)state).naturality$ownerId();
        Vec3 origin = new Vec3(state.x, state.y + FishingLineRendering.ANCHOR_Y, state.z);
        // Resolve after all entity item layers have submitted their poses, so
        // hook/player submission order cannot produce an old-frame endpoint.
        FishingLineRendering.submit(pose, collector, "player-fishing:" + ownerId, origin, () -> {
            var client = Minecraft.getInstance();
            Vec3 tip = client.player != null && ownerId == client.player.getId()
                    && client.options.getCameraType().isFirstPerson()
                ? naturality.client.villager.FirstPersonRodTip.worldTip
                : naturality.client.villager.PlayerRodTips.tips.get(ownerId);
            return tip == null ? fallback : tip.subtract(origin);
        });
    }
}

