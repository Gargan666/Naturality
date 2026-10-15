package naturality.client.mixin;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import naturality.client.entity.ZombieWalk;
import naturality.client.entity.ZombieWalkState;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(LivingEntityRenderer.class)
public abstract class ZombieWalkRendererMixin {
    @Inject(method="extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V",at=@At("TAIL"))
    private void naturality$extract(LivingEntity entity,LivingEntityRenderState state,float partial,CallbackInfo ci) {
        ((ZombieWalkState)state).naturality$walkLean(ZombieWalk.pose(entity,partial,state.walkAnimationPos));
    }
    @Inject(method="setupRotations",at=@At("TAIL"))
    private void naturality$walk(LivingEntityRenderState state,PoseStack pose,float body,float scale,CallbackInfo ci) {
        var lean=((ZombieWalkState)state).naturality$walkLean();
        if(lean.x==0 && lean.y==0) return;
        // The living renderer origin is at the feet; do not translate to the body center.
        pose.rotateDegrees(Axis.XP,-lean.x); pose.rotateDegrees(Axis.ZP,lean.y);
    }
}
