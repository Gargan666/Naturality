package naturality.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import naturality.client.villager.FirstPersonRodTip;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(FirstPersonHandsAndItemsRenderer.class)
public abstract class FirstPersonRodTipMixin {
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
