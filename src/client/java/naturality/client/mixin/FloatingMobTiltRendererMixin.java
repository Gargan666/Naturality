package naturality.client.mixin;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import naturality.client.entity.FloatingMobTilt;
import naturality.client.entity.FloatingMobTiltState;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(LivingEntityRenderer.class)
public abstract class FloatingMobTiltRendererMixin {
    @Inject(method="extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V",at=@At("TAIL"))
    private void naturality$extract(LivingEntity entity,LivingEntityRenderState state,float partial,CallbackInfo ci) {
        ((FloatingMobTiltState)state).naturality$floatLean(FloatingMobTilt.lean(entity,state.bodyRot,partial));
    }
    @Inject(method="setupRotations",at=@At("TAIL"))
    private void naturality$rotate(LivingEntityRenderState state,PoseStack pose,float body,float scale,CallbackInfo ci) {
        var lean=((FloatingMobTiltState)state).naturality$floatLean();
        if(lean.x==0 && lean.y==0) return;
        float pivot=state.boundingBoxHeight/scale/2;
        if(state instanceof net.minecraft.client.renderer.entity.state.GhastRenderState) pivot=1.8045F;
        if(state instanceof net.minecraft.client.renderer.entity.state.HappyGhastRenderState) pivot=2.004F*(state.isBaby?.2375F:1);
        pose.translate(0,pivot,0);
        // This hook precedes the living renderer's mirrored model axes.
        pose.rotateDegrees(Axis.XP,-lean.x); pose.rotateDegrees(Axis.ZP,lean.y);
        pose.translate(0,-pivot,0);
    }
}
