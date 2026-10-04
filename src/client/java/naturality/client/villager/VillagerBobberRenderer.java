package naturality.client.villager;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;
import naturality.villager.VillagerBobber;
import naturality.villager.VillagerWorkVisuals;

/** Independent villager hook renderer using the shared bobber model. */
public final class VillagerBobberRenderer extends EntityRenderer<VillagerBobber, VillagerBobberRenderState> {
    private final ItemModelResolver itemResolver;

    public VillagerBobberRenderer(EntityRendererProvider.Context context) {
        super(context);
        itemResolver = context.getItemModelResolver();
    }

    @Override public VillagerBobberRenderState createRenderState() { return new VillagerBobberRenderState(); }

    @Override public void extractRenderState(VillagerBobber entity, VillagerBobberRenderState state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        var owner = entity.owner();
        state.fishingOwnerId = owner == null ? -1 : owner.getId();
        state.glint = FishingBobberModel.rodGlint(owner);
        if (owner != null) {
            Vec3 tip = ((VillagerWorkVisuals)owner).naturality$renderedRodTip();
            if (tip == null || tip.distanceToSqr(owner.position()) > 9) {
                float yaw = Mth.lerp(partialTicks, owner.yBodyRotO, owner.yBodyRot) * Mth.DEG_TO_RAD;
                tip = owner.getPosition(partialTicks).add(-Mth.cos(yaw) * 0.27 - Mth.sin(yaw) * 0.44,
                    1.3, -Mth.sin(yaw) * 0.27 + Mth.cos(yaw) * 0.44);
            }
            state.lineOriginOffset = tip.subtract(entity.getPosition(partialTicks).add(0, FishingLineRendering.ANCHOR_Y, 0));
        } else state.lineOriginOffset = Vec3.ZERO;
        state.retrieving = entity.phase() == VillagerBobber.RETRIEVING;
        if (state.retrieving && !entity.catchItem().isEmpty())
            itemResolver.updateForNonLiving(state.caughtItem, entity.catchItem(), ItemDisplayContext.FIXED, entity);
        else state.caughtItem.clear();
    }

    @Override public void submit(VillagerBobberRenderState state, PoseStack poseStack,
            SubmitNodeCollector collector, CameraRenderState camera) {
        poseStack.pushPose();
        FishingBobberModel.submit(poseStack, collector, state.lightCoords, state.glint);
        if (state.retrieving && !state.caughtItem.isEmpty()) {
            poseStack.pushPose();
            poseStack.translate(0, -0.10, 0);
            poseStack.rotate(camera.orientation);
            poseStack.scale(0.36F, 0.36F, 0.36F);
            state.caughtItem.submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
            poseStack.popPose();
        }
        Vec3 origin = new Vec3(state.x, state.y + FishingLineRendering.ANCHOR_Y, state.z);
        Vec3 endpoint = state.lineOriginOffset;
        FishingLineRendering.submit(poseStack, collector, "villager-fishing:" + state.fishingOwnerId,
            origin, () -> endpoint);
        poseStack.popPose();
        super.submit(state, poseStack, collector, camera);
    }

}
