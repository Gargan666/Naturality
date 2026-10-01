package naturality.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import naturality.client.glint.GlintContours;
import naturality.client.portal.FlatModelAlpha;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.entity.layers.EquipmentLayerRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.UvMapping;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(EquipmentLayerRenderer.class)
public abstract class GlintEquipmentContourMixin {
    @WrapOperation(method = "renderLayers(Lnet/minecraft/client/resources/model/EquipmentClientInfo$LayerType;Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lnet/minecraft/world/item/ItemStack;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/resources/Identifier;II)V", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/OrderedSubmitNodeCollector;submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/rendertype/RenderType;IIILnet/minecraft/client/renderer/texture/UvMapping;I)V"))
    private <S> void naturality$armor(OrderedSubmitNodeCollector collector, Model<? super S> model,
            S state, PoseStack pose, RenderType type, int light, int overlay, int color,
            @org.jspecify.annotations.Nullable UvMapping uv, int outline, Operation<Void> original, @Local(argsOnly = true) ItemStack stack) {
        original.call(collector, model, state, pose, type, light, overlay, color, uv, outline);
        if (!stack.hasFoil()) return;
        boolean procedural = type.pipeline() == RenderPipelines.ARMOR_CUTOUT_NO_CULL_GLINT;
        // Base layers define the silhouette, including enchanted trimmed armor.
        if (!procedural && type.pipeline() != RenderPipelines.ARMOR_CUTOUT_NO_CULL) return;
        var buffer = GlintContours.armor(FlatModelAlpha.texture(type), procedural);
        if (buffer == null) return;
        model.setupAnim(state);
        var mapped = uv == null ? buffer : uv.wrap(buffer);
        if (mapped != null) model.renderToBuffer(pose, mapped, light, overlay, color);
    }
}
