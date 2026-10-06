package naturality.client.mixin;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import com.mojang.blaze3d.vertex.PoseStack;
import naturality.client.weather.LightningRendering;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LightningBoltRenderer;
import net.minecraft.client.renderer.entity.state.LightningBoltRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
@Mixin(LightningBoltRenderer.class)
public abstract class LightningRendererMixin {
    @WrapOperation(method="submit",at=@At(value="INVOKE",target="Lnet/minecraft/client/renderer/rendertype/RenderTypes;lightning()Lnet/minecraft/client/renderer/rendertype/RenderType;"))
    private RenderType naturality$unfogged(Operation<RenderType> original) { return LightningRendering.TYPE; }
    @WrapMethod(method="submit")
    private void naturality$distantWidth(LightningBoltRenderState state,PoseStack pose,SubmitNodeCollector collector,CameraRenderState camera,Operation<Void> original) {
        float width=(float)Math.max(1,Math.hypot(state.x-camera.pos.x,state.z-camera.pos.z)/256);
        pose.pushPose();pose.scale(width,1,width);
        try { original.call(state,pose,collector,camera); } finally { pose.popPose(); }
    }
}
