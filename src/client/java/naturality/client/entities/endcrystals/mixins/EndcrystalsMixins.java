package naturality.client.entities.endcrystals.mixins;

import java.util.function.Function;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.object.crystal.EndCrystalModel;
import net.minecraft.client.model.geom.ModelPart;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import naturality.client.entities.endcrystals.CrystalVisuals;
import naturality.Naturality;
import net.minecraft.client.renderer.entity.EndCrystalRenderer;
import net.minecraft.client.renderer.entity.state.EndCrystalRenderState;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;

/** Feature integration hooks; each nested type retains its independent Mixin target and gate. */
public final class EndcrystalsMixins {
    private EndcrystalsMixins() {}

    @Mixin(Model.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class CrystalVisualMaterialMixin {
        @Inject(method="renderType(Lnet/minecraft/resources/Identifier;)Lnet/minecraft/client/renderer/rendertype/RenderType;",at=@At("HEAD"),cancellable=true)
        private void naturality$cull(Identifier texture,CallbackInfoReturnable<RenderType> cir) {
            if((Object)this instanceof naturality.client.entities.endcrystals.CrystalCoreModel) cir.setReturnValue(naturality.client.entities.endcrystals.CrystalCoreModel.LAYER);
        }
        @Inject(method="renderType()Ljava/util/function/Function;",at=@At("HEAD"),cancellable=true)
        private void naturality$cullFactory(CallbackInfoReturnable<Function<Identifier,RenderType>> cir) {
            if((Object)this instanceof naturality.client.entities.endcrystals.CrystalCoreModel) cir.setReturnValue(_ -> naturality.client.entities.endcrystals.CrystalCoreModel.LAYER);
        }
    }

    @Mixin(EndCrystalModel.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class CrystalVisualModelMixin {
        @Inject(method="setupAnim(Lnet/minecraft/client/renderer/entity/state/EndCrystalRenderState;)V",at=@At("TAIL"))
        private void naturality$splitCore(net.minecraft.client.renderer.entity.state.EndCrystalRenderState state,CallbackInfo ci) {
            if(!((Object)this instanceof naturality.client.entities.endcrystals.CrystalCoreModel)) ((EndCrystalModel)(Object)this).cube.visible=false;
        }
        @ModifyConstant(method="setupAnim(Lnet/minecraft/client/renderer/entity/state/EndCrystalRenderState;)V",constant=@Constant(floatValue=3F))
        private float naturality$spin(float original) { return original*.5F; }
        @Inject(method="<init>",at=@At("TAIL"))
        private void naturality$inside(ModelPart root,CallbackInfo ci) {
            ((EndCrystalModel)(Object)this).cube.visit(new PoseStack(),(_,_,_,cube) -> {
                for(int i=0;i<cube.polygons.length;i++) {
                    var face=cube.polygons[i];var source=face.vertices();
                    var vertices=new ModelPart.Vertex[source.length];
                    for(int j=0;j<source.length;j++) vertices[j]=source[source.length-1-j];
                    cube.polygons[i]=new ModelPart.Polygon(vertices,new Vector3f(face.normal()).negate());
                }
            });
        }
    }

    @Mixin(EndCrystalRenderer.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class CrystalVisualRendererMixin {
        @org.spongepowered.asm.mixin.Unique private naturality.client.entities.endcrystals.@org.jspecify.annotations.Nullable CrystalCoreModel naturality$core;
        @com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation(method="submit(Lnet/minecraft/client/renderer/entity/state/EndCrystalRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",at=@At(value="INVOKE",target="Lnet/minecraft/client/renderer/SubmitNodeCollector;submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/resources/Identifier;III)V"))
        private void naturality$emissiveCore(net.minecraft.client.renderer.SubmitNodeCollector collector,net.minecraft.client.model.Model<?> model,Object state,com.mojang.blaze3d.vertex.PoseStack pose,Identifier texture,int light,int overlay,int outline,com.llamalad7.mixinextras.injector.wrapoperation.Operation<Void> original) {
            original.call(collector,model,state,pose,texture,light,overlay,outline);
            var core = naturality$core;
            if(core==null) { core=new naturality.client.entities.endcrystals.CrystalCoreModel(); naturality$core=core; }
            collector.submitModel(core,(EndCrystalRenderState)state,pose,naturality.client.entities.endcrystals.CrystalCoreModel.LAYER,15728880,overlay,outline);
        }
        @Inject(method="getY",at=@At("HEAD"),cancellable=true)
        private static void naturality$bob(float age,CallbackInfoReturnable<Float> cir) { cir.setReturnValue(CrystalVisuals.bob(age)); }
        @ModifyExpressionValue(method="submit(Lnet/minecraft/client/renderer/entity/state/EndCrystalRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",at=@At(value="FIELD",target="Lnet/minecraft/client/renderer/entity/EndCrystalRenderer;END_CRYSTAL_LOCATION:Lnet/minecraft/resources/Identifier;"))
        private Identifier naturality$texture(Identifier original) { return Naturality.id("textures/entity/end_crystal.png"); }
    }
}
