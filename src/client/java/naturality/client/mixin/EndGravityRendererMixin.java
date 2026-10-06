package naturality.client.mixin;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import naturality.client.weather.EndGravityRenderState;
import naturality.weather.EndGravity;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(LivingEntityRenderer.class)
public abstract class EndGravityRendererMixin {
    @Inject(method="extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V",at=@At("TAIL"))
    private void naturality$extract(LivingEntity e,LivingEntityRenderState state,float partial,CallbackInfo ci) {
        float inversion=EndGravity.inversion(e,true);
        ((EndGravityRenderState)state).naturality$inversion(inversion);
        if(inversion==0)return;
        // World look direction expressed in the rolled body's local frame.
        // At a full flip both pitch and relative head yaw reverse; projecting
        // the direction also keeps the head tracking correctly during the turn.
        double yaw=Math.toRadians(state.yRot),pitch=Math.toRadians(state.xRot);
        double angle=Math.PI*inversion,cos=Math.cos(angle),sin=Math.sin(angle);
        double x=Math.sin(yaw)*Math.cos(pitch),y=-Math.sin(pitch),z=-Math.cos(yaw)*Math.cos(pitch);
        double localX=cos*x+sin*y,localY=-sin*x+cos*y;
        state.yRot=(float)Math.toDegrees(Math.atan2(localX,-z));
        state.xRot=(float)Math.toDegrees(Math.atan2(-localY,Math.hypot(localX,z)));
    }
    @Inject(method="setupRotations",at=@At("TAIL"))
    private void naturality$rotate(LivingEntityRenderState state,PoseStack pose,float body,float scale,CallbackInfo ci) {
        float inversion=((EndGravityRenderState)state).naturality$inversion();if(inversion==0)return;
        float half=state.boundingBoxHeight/scale/2;
        pose.translate(0,half,0);pose.rotateDegrees(Axis.ZP,inversion*180);pose.translate(0,-half,0);
    }
}
