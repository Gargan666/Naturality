package naturality.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import naturality.client.insanity.InsanityState;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EnderDragonRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** The dragon uses EntityRenderState and bypasses LivingEntityRenderer's body fade. */
@Mixin(EnderDragonRenderer.class)
public abstract class InsanityDragonRendererMixin {
    @WrapOperation(method="submit", at=@At(value="INVOKE", target="Lnet/minecraft/client/renderer/SubmitNodeCollector;submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/resources/Identifier;III)V"))
    private <S extends @org.jspecify.annotations.NonNull Object> void naturality$body(SubmitNodeCollector collector, Model<? super S> model, S state, PoseStack pose,
            Identifier texture, int light, int overlay, int outline, Operation<Void> original) {
        float alpha=((InsanityState)state).naturality$insanity().alpha;
        if(alpha==1) original.call(collector,model,state,pose,texture,light,overlay,outline);
        else if(alpha>0) collector.submitModel(model,state,pose,RenderTypes.entityTranslucentCull(texture),light,overlay,ARGB.white(alpha),null,outline);
    }
    @WrapOperation(method="submit", at=@At(value="INVOKE", target="Lnet/minecraft/client/renderer/SubmitNodeCollector;submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/rendertype/RenderType;III)V"))
    private <S extends @org.jspecify.annotations.NonNull Object> void naturality$eyes(SubmitNodeCollector collector, Model<? super S> model, S state, PoseStack pose,
            RenderType type, int light, int overlay, int outline, Operation<Void> original) {
        if(((InsanityState)state).naturality$insanity().alpha==1)
            original.call(collector,model,state,pose,type,light,overlay,outline);
    }
}
