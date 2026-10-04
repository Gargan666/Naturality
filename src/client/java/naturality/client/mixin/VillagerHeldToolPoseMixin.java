package naturality.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import naturality.client.villager.VillagerVisualState;
import net.minecraft.client.renderer.entity.layers.CrossedArmsItemLayer;
import net.minecraft.client.renderer.entity.state.HoldingEntityRenderState;
import net.minecraft.client.renderer.entity.state.VillagerRenderState;
import org.joml.Vector3f;
import org.joml.Quaternionf;
import net.minecraft.world.phys.Vec3;
import naturality.villager.VillagerWorkVisuals;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CrossedArmsItemLayer.class)
public abstract class VillagerHeldToolPoseMixin {
    @Inject(method = "applyTranslation", at = @At("TAIL"))
    private void naturality$workToolPose(HoldingEntityRenderState state, PoseStack poseStack, CallbackInfo ci) {
        if (state instanceof VillagerRenderState villager && ((VillagerVisualState)state).naturality$toolPose()) {
            var visual = (VillagerVisualState)villager;
            boolean swordPose = visual.naturality$owner() != null
                && visual.naturality$owner().getMainHandItem().is(net.minecraft.tags.ItemTags.SWORDS);
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
                    if (visual.naturality$rodTipValid() && visual.naturality$owner() != null) {
                        Vector3f point = visual.naturality$rodTip();
                        // Generated sprite extents include the transparent border
                        // beyond the final rod pixel.
                        point.sub(new Vector3f(alongRod).mul(0.075F));
                        ((VillagerWorkVisuals)visual.naturality$owner()).naturality$setRenderedRodTip(
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
