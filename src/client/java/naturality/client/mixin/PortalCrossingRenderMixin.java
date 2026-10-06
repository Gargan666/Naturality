package naturality.client.mixin;
import com.mojang.blaze3d.vertex.PoseStack;
import naturality.client.portal.PortalCrossingClient;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
@Mixin(EntityRenderDispatcher.class)
public abstract class PortalCrossingRenderMixin {
    @com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod(method="submit")
    private void naturality$clipBody(EntityRenderState state,CameraRenderState camera,double x,double y,double z,
            PoseStack poses,SubmitNodeCollector collector,com.llamalad7.mixinextras.injector.wrapoperation.Operation<Void> original) {
        original.call(state,camera,x,y,z,poses,PortalCrossingClient.clip(state,camera.pos,collector));
    }
    @Inject(method="extractEntity",at=@At("RETURN"))
    private void naturality$entrySide(Entity entity,float partial,CallbackInfoReturnable<EntityRenderState> ci){
        PortalCrossingClient.remember(entity,ci.getReturnValue());
    }
    @Inject(method="submit",at=@At("HEAD"),cancellable=true)
    private void naturality$hideExit(EntityRenderState state,CameraRenderState camera,double x,double y,double z,
            PoseStack poses,SubmitNodeCollector collector,CallbackInfo ci){
        if(PortalCrossingClient.hidden(state,camera.pos)) ci.cancel();
    }
}
