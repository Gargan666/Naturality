package naturality.client.mixin;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import naturality.client.entity.FishSwim;
import naturality.client.entity.FishSwimState;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.fish.AbstractFish;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(LivingEntityRenderer.class)
public abstract class FishSwimRendererMixin {
    @Inject(method="extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V",at=@At("TAIL"))
    private void naturality$extract(LivingEntity entity,LivingEntityRenderState state,float partial,CallbackInfo ci) {
        var swim=entity instanceof AbstractFish fish && fish.isInWater() && fish.isAlive()?FishSwim.pose(fish,partial):null;
        ((FishSwimState)state).naturality$swim(swim);
        if(swim!=null) { state.bodyRot=swim.yaw();state.ageInTicks=swim.phase(); }
    }
    @Inject(method="setupRotations",at=@At("TAIL"))
    private void naturality$rotate(LivingEntityRenderState state,PoseStack pose,float body,float scale,CallbackInfo ci) {
        var swim=((FishSwimState)state).naturality$swim();
        if(swim!=null) {
            float center=state.boundingBoxHeight/scale/2;
            pose.translate(0,center,0);pose.rotateDegrees(Axis.XP,-swim.pitch());pose.translate(0,-center,0);
        }
    }
}
