package naturality.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.VertexConsumer;
import naturality.client.glint.GlintContours;
import naturality.client.portal.FlatModelAlpha;
import net.minecraft.client.renderer.feature.ItemFeatureRenderer;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.world.item.ItemDisplayContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ItemFeatureRenderer.class)
public abstract class GlintItemContourMixin {
    @ModifyExpressionValue(method = "prepareMainSubmit", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/feature/ItemFeatureRenderer;getVertexBuilder(Lnet/minecraft/client/renderer/rendertype/RenderType;)Lcom/mojang/blaze3d/vertex/VertexConsumer;"))
    private VertexConsumer naturality$contour(VertexConsumer original,
            @Local(argsOnly = true) ItemFeatureRenderer.Submit submit, @Local RenderType renderType) {
        if ((submit.displayContext() != ItemDisplayContext.GROUND && submit.displayContext() != ItemDisplayContext.FIXED && submit.displayContext() != ItemDisplayContext.HEAD && submit.displayContext() != ItemDisplayContext.THIRD_PERSON_LEFT_HAND && submit.displayContext() != ItemDisplayContext.THIRD_PERSON_RIGHT_HAND && submit.displayContext() != ItemDisplayContext.FIRST_PERSON_LEFT_HAND && submit.displayContext() != ItemDisplayContext.FIRST_PERSON_RIGHT_HAND) || submit.foilType() == ItemStackRenderState.FoilType.NONE) return original;
        return GlintContours.capture(original, FlatModelAlpha.texture(renderType), false);
    }
}


