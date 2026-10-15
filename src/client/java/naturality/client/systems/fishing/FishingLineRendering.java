package naturality.client.systems.fishing;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.joml.Matrix4f;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import naturality.systems.fishing.VillagerBobbers.VillagerBobber;
import naturality.systems.villagerwork.framework.VillagerWork.VillagerWorkVisuals;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import naturality.systems.fishing.VillagerBobbers.PlayerFishingReturn;

/** Shared player/villager line, anchored at the top of the authored bobber stem. */
public final class FishingLineRendering {
    static { naturality.client.api.ropes.RopeRendering.initialize(); }
    public static final float ANCHOR_Y = .07F + 4.0F / 16.0F * 1.3F;
    public static final float VANILLA_ANCHOR_Y = .25F;

    private FishingLineRendering() { }

    public static void submit(PoseStack poseStack, SubmitNodeCollector collector, Object key,
            Vec3 origin, java.util.function.Supplier<Vec3> endpoint) {
        var client = Minecraft.getInstance();
        var frame = client.gameRenderer.gameRenderState();
        float pixelSize = frame.optionsRenderState.cameraType.isFirstPerson()
            && FirstPersonRodTip.worldTip != null ? FirstPersonRodTip.pixelSize : 1.0F / 32;
        naturality.client.api.ropes.RopeRendering.submitRope(poseStack, collector, key, origin, () -> origin.add(endpoint.get()),
            new Vec3(0, ANCHOR_Y, 0), pixelSize, _ -> -16777216);
    }


    /** Current-frame hand-model measurement, filled before world submission. */
    public static final class FirstPersonRodTip {
        public static boolean measuring;
        public static Vector3f viewTip;
        public static Vec3 worldTip;
        public static float pixelSize = 1.0F / 32;
        private FirstPersonRodTip() { }
    }

    public interface FishingOwnerState {
        int naturality$ownerId();
        void naturality$setOwnerId(int id);
        boolean naturality$glint();
        void naturality$setGlint(boolean value);
    }

    /** Rod tips measured from this frame's animated player item submissions. */
    public static final class PlayerRodTips {
        public static final Map<AvatarRenderState, Matrix4f> roots = new IdentityHashMap<>();
        public static final Map<Integer, Vec3> tips = new HashMap<>();
        private PlayerRodTips() { }
        public static void clear() { roots.clear(); tips.clear(); }
    }

    /** Independent villager hook renderer using the shared bobber model. */
    public static final class VillagerBobberRenderer extends EntityRenderer<VillagerBobber, VillagerBobberRenderState> {
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
                Vec3 tip = owner instanceof VillagerWorkVisuals visuals ? visuals.naturality$renderedRodTip() : null;
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

    public static final class VillagerBobberRenderState extends EntityRenderState {
        public Vec3 lineOriginOffset = Vec3.ZERO;
        public final ItemStackRenderState caughtItem = new ItemStackRenderState();
        public boolean retrieving;
        public boolean glint;
        public boolean firstPersonOwner;
        public int fishingOwnerId = -1;
    }

    /** The same model and hooked item as the villager's brief return animation. */
    public static final class PlayerFishingReturnRenderer extends EntityRenderer<PlayerFishingReturn, VillagerBobberRenderState> {
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
}
