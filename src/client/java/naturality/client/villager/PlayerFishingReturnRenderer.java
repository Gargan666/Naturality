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
import naturality.villager.PlayerFishingReturn;

/** The same model and hooked item as the villager's brief return animation. */
public final class PlayerFishingReturnRenderer extends EntityRenderer<PlayerFishingReturn, VillagerBobberRenderState> {
    private final ItemModelResolver itemResolver;

    public PlayerFishingReturnRenderer(EntityRendererProvider.Context context) {
        super(context);
        itemResolver = context.getItemModelResolver();
    }

    @Override public VillagerBobberRenderState createRenderState() { return new VillagerBobberRenderState(); }

    @Override public void extractRenderState(PlayerFishingReturn entity, VillagerBobberRenderState state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        var owner = entity.owner();
        state.glint = entity.rodGlint();
        state.fishingOwnerId = owner == null ? -1 : owner.getId();
        state.firstPersonOwner = owner == net.minecraft.client.Minecraft.getInstance().player;
        if (owner != null) {
            float yaw = Mth.lerp(partialTicks, owner.yBodyRotO, owner.yBodyRot) * Mth.DEG_TO_RAD;
            Vec3 hand = owner.getEyePosition(partialTicks).add(-Mth.cos(yaw) * .35 - Mth.sin(yaw) * .8,
                -.45, -Mth.sin(yaw) * .35 + Mth.cos(yaw) * .8);
            state.lineOriginOffset = hand.subtract(entity.getPosition(partialTicks).add(0, FishingLineRendering.ANCHOR_Y, 0));
        } else state.lineOriginOffset = Vec3.ZERO;
        state.retrieving = true;
        itemResolver.updateForNonLiving(state.caughtItem, entity.catchItem(), ItemDisplayContext.FIXED, entity);
    }

    @Override public void submit(VillagerBobberRenderState state, PoseStack pose,
            SubmitNodeCollector collector, CameraRenderState camera) {
        pose.pushPose();
        FishingBobberModel.submit(pose, collector, state.lightCoords, state.glint);
        if (!state.caughtItem.isEmpty()) {
            pose.pushPose();
            pose.translate(0, -.10, 0);
            pose.rotate(camera.orientation);
            pose.scale(.36F, .36F, .36F);
            state.caughtItem.submit(pose, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
            pose.popPose();
        }
        Vec3 fallback = state.lineOriginOffset;
        Vec3 origin = new Vec3(state.x, state.y + FishingLineRendering.ANCHOR_Y, state.z);
        int ownerId = state.fishingOwnerId;
        boolean localOwner = state.firstPersonOwner;
        FishingLineRendering.submit(pose, collector, "player-fishing:" + ownerId, origin, () -> {
            Vec3 tip = localOwner && net.minecraft.client.Minecraft.getInstance().options.getCameraType().isFirstPerson()
                ? FirstPersonRodTip.worldTip : PlayerRodTips.tips.get(ownerId);
            return tip == null ? fallback : tip.subtract(origin);
        });
        pose.popPose();
        super.submit(state, pose, collector, camera);
    }
}
