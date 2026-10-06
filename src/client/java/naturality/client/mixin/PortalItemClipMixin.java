package naturality.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.*;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.*;
import naturality.client.portal.PortalClippedCollector;
import net.minecraft.client.renderer.feature.ItemFeatureRenderer;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ItemFeatureRenderer.class)
public abstract class PortalItemClipMixin {
    @WrapOperation(method={"prepareMainSubmit","prepareOutlineSubmit"},at=@At(value="INVOKE",
        target="Lcom/mojang/blaze3d/vertex/VertexConsumer;putBakedQuad(Lcom/mojang/blaze3d/vertex/PoseStack$Pose;Lnet/minecraft/client/resources/model/geometry/BakedQuad;Lcom/mojang/blaze3d/vertex/QuadInstance;)V"))
    private void naturality$quad(VertexConsumer buffer,PoseStack.Pose pose,BakedQuad quad,QuadInstance instance,
        Operation<Void> original,@Local(argsOnly=true) ItemFeatureRenderer.Submit submit) {
        var plane=PortalClippedCollector.plane(submit);
        if(plane==null) {original.call(buffer,pose,quad,instance);return;}
        var clipped=plane.wrap(buffer);original.call(clipped,pose,quad,instance);clipped.finish();
    }
    @WrapOperation(method="prepareMainSubmit",at=@At(value="INVOKE",
        target="Lcom/mojang/blaze3d/vertex/VertexConsumer;putBakedQuadWithGlint(Lcom/mojang/blaze3d/vertex/PoseStack$Pose;Lnet/minecraft/client/resources/model/geometry/BakedQuad;Lcom/mojang/blaze3d/vertex/QuadInstance;Lcom/mojang/blaze3d/vertex/PoseStack$Pose;)V"))
    private void naturality$glint(VertexConsumer buffer,PoseStack.Pose pose,BakedQuad quad,QuadInstance instance,PoseStack.Pose decal,
        Operation<Void> original,@Local(argsOnly=true) ItemFeatureRenderer.Submit submit) {
        var plane=PortalClippedCollector.plane(submit);
        if(plane==null) {original.call(buffer,pose,quad,instance,decal);return;}
        var clipped=plane.wrap(buffer);original.call(clipped,pose,quad,instance,decal);clipped.finish();
    }
}
