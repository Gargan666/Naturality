package naturality.client.mixin;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.object.crystal.EndCrystalModel;
import net.minecraft.client.model.geom.ModelPart;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(EndCrystalModel.class)
public abstract class CrystalVisualModelMixin {
    @Inject(method="setupAnim(Lnet/minecraft/client/renderer/entity/state/EndCrystalRenderState;)V",at=@At("TAIL"))
    private void naturality$splitCore(net.minecraft.client.renderer.entity.state.EndCrystalRenderState state,CallbackInfo ci) {
        if(!((Object)this instanceof naturality.client.entity.CrystalCoreModel)) ((EndCrystalModel)(Object)this).cube.visible=false;
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

