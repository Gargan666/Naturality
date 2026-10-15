package naturality.client.mixin;
import naturality.client.entity.CrystalVisuals;
import naturality.Naturality;
import net.minecraft.client.renderer.entity.EndCrystalRenderer;
import net.minecraft.client.renderer.entity.state.EndCrystalRenderState;

import net.minecraft.resources.Identifier;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
@Mixin(EndCrystalRenderer.class)
public abstract class CrystalVisualRendererMixin {
    @org.spongepowered.asm.mixin.Unique private naturality.client.entity.@org.jspecify.annotations.Nullable CrystalCoreModel naturality$core;
    @com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation(method="submit(Lnet/minecraft/client/renderer/entity/state/EndCrystalRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",at=@At(value="INVOKE",target="Lnet/minecraft/client/renderer/SubmitNodeCollector;submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/resources/Identifier;III)V"))
    private void naturality$emissiveCore(net.minecraft.client.renderer.SubmitNodeCollector collector,net.minecraft.client.model.Model<?> model,Object state,com.mojang.blaze3d.vertex.PoseStack pose,Identifier texture,int light,int overlay,int outline,com.llamalad7.mixinextras.injector.wrapoperation.Operation<Void> original) {
        original.call(collector,model,state,pose,texture,light,overlay,outline);
        var core = naturality$core;
        if(core==null) { core=new naturality.client.entity.CrystalCoreModel(); naturality$core=core; }
        collector.submitModel(core,(EndCrystalRenderState)state,pose,naturality.client.entity.CrystalCoreModel.LAYER,15728880,overlay,outline);
    }
    @Inject(method="getY",at=@At("HEAD"),cancellable=true)
    private static void naturality$bob(float age,CallbackInfoReturnable<Float> cir) { cir.setReturnValue(CrystalVisuals.bob(age)); }
    @ModifyExpressionValue(method="submit(Lnet/minecraft/client/renderer/entity/state/EndCrystalRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",at=@At(value="FIELD",target="Lnet/minecraft/client/renderer/entity/EndCrystalRenderer;END_CRYSTAL_LOCATION:Lnet/minecraft/resources/Identifier;"))
    private Identifier naturality$texture(Identifier original) { return Naturality.id("textures/entity/end_crystal.png"); }
}
