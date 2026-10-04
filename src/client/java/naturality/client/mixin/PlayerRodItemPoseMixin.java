package naturality.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import naturality.client.villager.PlayerRodTips;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ItemInHandLayer.class)
public abstract class PlayerRodItemPoseMixin {
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
