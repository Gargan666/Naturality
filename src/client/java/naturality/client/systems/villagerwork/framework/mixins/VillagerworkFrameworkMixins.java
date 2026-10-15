package naturality.client.systems.villagerwork.framework.mixins;

import naturality.client.systems.villagerwork.framework.VillagerVisualState;
import net.minecraft.client.model.npc.VillagerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.VillagerRenderState;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.entity.layers.CrossedArmsItemLayer;
import net.minecraft.client.renderer.entity.state.HoldingEntityRenderState;
import org.joml.Vector3f;
import org.joml.Quaternionf;
import net.minecraft.world.phys.Vec3;
import naturality.systems.villagerwork.framework.VillagerWork.VillagerWorkVisuals;
import naturality.client.systems.fishing.mixins.FishingMixins.LivingEntityItemResolverAccess;
import net.minecraft.client.renderer.entity.VillagerRenderer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Unique;

/** Feature integration hooks; each nested type retains its independent Mixin target and gate. */
public final class VillagerworkFrameworkMixins {
    private VillagerworkFrameworkMixins() {}

    @Mixin(VillagerModel.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class VillagerArmSwingMixin {
        @SuppressWarnings("null") @Shadow @Final private ModelPart arms;
        @SuppressWarnings("null") @Shadow @Final private ModelPart head;

        @Inject(method = "setupAnim", at = @At("TAIL"))
        private void naturality$workSwing(VillagerRenderState state, CallbackInfo ci) {
            var owner = ((VillagerVisualState)state).naturality$owner();
            if (owner == null) return;
            float nod = naturality.config.startup.FeatureConfig.Feature.REPUTATION.enabled()
                ? state.ageInTicks - naturality.systems.reputation.Reputation.state(owner).nodStart : 20;
            if (nod >= 0 && nod < 20) head.xRot += Mth.sin(nod * (float)Math.PI / 5) * .3F * (1 - nod / 20);
            var reputation = naturality.systems.reputation.Reputation.state(owner);
            float gossipAge = state.ageInTicks - reputation.gossipStart;
            int conversationDuration = naturality.systems.reputation.Reputation.GossipReactions.duration(reputation.gossipReaction);
            if (naturality.config.startup.FeatureConfig.Feature.REPUTATION.enabled() && gossipAge >= 0 && gossipAge < conversationDuration) {
                float envelope = Math.min(1, gossipAge / 5) * Math.min(1, (conversationDuration - gossipAge) / 10);
                if (reputation.gossipReaction == naturality.systems.reputation.Reputation.GossipReactions.LISTEN_NEGATIVE)
                    head.yRot += Mth.sin(gossipAge * (float)Math.PI / 8) * .3F * envelope;
                else {
                    float bob = (1 - Mth.cos(gossipAge * (float)Math.PI / 10)) * .5F * envelope;
                    float direction = reputation.gossipReaction == naturality.systems.reputation.Reputation.GossipReactions.TELL_POSITIVE
                        || reputation.gossipReaction == naturality.systems.reputation.Reputation.GossipReactions.CONVERSE ? -1 : 1;
                    float amplitude = reputation.gossipReaction == naturality.systems.reputation.Reputation.GossipReactions.CONVERSE ? .08F
                        : reputation.gossipReaction >= naturality.systems.reputation.Reputation.GossipReactions.TELL_POSITIVE ? .16F : .25F;
                    head.xRot += bob * amplitude * direction;
                }
            }
            float swing = owner.getSwingAnimation(Mth.clamp(state.ageInTicks - owner.tickCount, 0, 1));
            if (swing > 0) {
                float forward = Mth.sin(swing * (float)Math.PI);
                float recovery = Mth.sin((1 - (1 - swing) * (1 - swing)) * (float)Math.PI);
                arms.xRot -= forward * 1.2F - recovery * 0.4F;
            }
        }
    }

    @Mixin(CrossedArmsItemLayer.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class VillagerHeldToolPoseMixin {
        @Inject(method = "applyTranslation", at = @At("TAIL"))
        private void naturality$workToolPose(HoldingEntityRenderState state, PoseStack poseStack, CallbackInfo ci) {
            if (state instanceof VillagerRenderState villager && ((VillagerVisualState)state).naturality$toolPose()) {
                var visual = (VillagerVisualState)villager;
                var owner = visual.naturality$owner();
                boolean swordPose = owner != null
                    && owner.getMainHandItem().is(net.minecraft.tags.ItemTags.SWORDS);
                if (visual.naturality$fishingRodPose() || swordPose) {
                    // Remove the held model's baked 55-degree sprite rotation.
                    // Keep the crossed-arm orientation and the sprite plane
                    // upright, with its horizontal direction flipped outward.
                    poseStack.translate(-0.08F, -0.02F, 0.0F);
                    Quaternionf bakedItemRotation = new Quaternionf().rotationXYZ(
                        0, swordPose ? -(float)Math.PI / 2 : (float)Math.PI / 2, (float)Math.toRadians(55));
                    poseStack.rotate(new Quaternionf().rotationY(-(float)Math.PI / 2)
                        .mul(bakedItemRotation.conjugate()));
                    // The model's own diagonal remains visible at player scale.
                    // CrossedArmsItemLayer already scales by 1.07; cancel that so the
                    // player third-person item transform retains its actual scale.
                    poseStack.scale(1.0F / 1.07F, 1.0F / 1.07F, 1.0F / 1.07F);
                    if (visual.naturality$fishingRodPose()) {
                        var itemPose = poseStack.last().pose();
                        var entityPose = visual.naturality$basePoseInverse();
                        Vector3f alongRod = new Vector3f(0.0F, 0.979F, 0.203F)
                            .mulDirection(itemPose).mulDirection(entityPose).normalize();
                        float[] furthest = {Float.NEGATIVE_INFINITY};
                        state.heldItem.visitExtents(extent -> {
                            Vector3f point = new Vector3f(extent).mulPosition(itemPose).mulPosition(entityPose);
                            float distance = point.dot(alongRod);
                            if (distance > furthest[0]) {
                                furthest[0] = distance;
                                visual.naturality$rodTip().set(point);
                                visual.naturality$setRodTipValid(true);
                            }
                        });
                        if (visual.naturality$rodTipValid() && owner != null) {
                            Vector3f point = visual.naturality$rodTip();
                            // Generated sprite extents include the transparent border
                            // beyond the final rod pixel.
                            point.sub(new Vector3f(alongRod).mul(0.075F));
                            ((VillagerWorkVisuals)owner).naturality$setRenderedRodTip(
                                new Vec3(villager.x + point.x, villager.y + point.y, villager.z + point.z));
                        }
                    }
                } else {
                    poseStack.translate(0.0F, -0.06F, -0.12F);
                    poseStack.rotate(Axis.YP, -0.38F);
                    poseStack.rotate(Axis.ZP, -0.4F);
                    poseStack.rotate(Axis.XP, -0.4F);
                    poseStack.scale(1.2F, 1.2F, 1.2F);
                }
            }
        }
    }

    @Mixin(VillagerRenderer.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class VillagerVisualExtractMixin {
        @Inject(method = "extractRenderState", at = @At("TAIL"))
        private void naturality$extract(Villager entity, VillagerRenderState state, float partialTicks, CallbackInfo ci) {
            var data = (VillagerVisualState)state;
            data.naturality$setOwner(entity);
            boolean toolPose = isWorkTool(entity.getMainHandItem())
                && !((VillagerWorkVisuals)entity).naturality$isDisplayingTrade();
            data.naturality$setToolPose(toolPose);
            data.naturality$setFishingRodPose(toolPose && entity.getMainHandItem().is(Items.FISHING_ROD));
            if (toolPose) ((LivingEntityItemResolverAccess)this).naturality$itemModelResolver().updateForLiving(
                state.heldItem, entity.getMainHandItem(), ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, entity);
        }

        private static boolean isWorkTool(ItemStack stack) {
            return stack.has(DataComponents.TOOL) || stack.is(ItemTags.SWORDS) || stack.is(Items.FISHING_ROD)
                || stack.is(Items.SHEARS) || stack.is(Items.BRUSH);
        }
    }

    @Mixin(VillagerRenderState.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class VillagerVisualStateMixin implements VillagerVisualState {
        @Unique private boolean naturality$toolPose;
        @Unique private boolean naturality$fishingRodPose;
        @Unique private final Matrix4f naturality$basePoseInverse = new Matrix4f();
        @Unique private final Vector3f naturality$rodTip = new Vector3f();
        @Unique private boolean naturality$rodTipValid;
        @Unique private @Nullable Villager naturality$owner;
        @Override public boolean naturality$toolPose() { return naturality$toolPose; }
        @Override public void naturality$setToolPose(boolean value) { naturality$toolPose = value; }
        @Override public boolean naturality$fishingRodPose() { return naturality$fishingRodPose; }
        @Override public void naturality$setFishingRodPose(boolean value) { naturality$fishingRodPose = value; }
        @Override public Matrix4f naturality$basePoseInverse() { return naturality$basePoseInverse; }
        @Override public Vector3f naturality$rodTip() { return naturality$rodTip; }
        @Override public boolean naturality$rodTipValid() { return naturality$rodTipValid; }
        @Override public void naturality$setRodTipValid(boolean value) { naturality$rodTipValid = value; }
        @Override public @Nullable Villager naturality$owner() { return naturality$owner; }
        @Override public void naturality$setOwner(@Nullable Villager owner) { naturality$owner = owner; }
    }
}
