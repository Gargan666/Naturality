package naturality.client.mixin;

import naturality.client.weather.EndGravityRenderState;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AvatarRenderer.class)
public abstract class EndGravityAvatarMixin {
    @Inject(method="getRenderOffset",at=@At("RETURN"),cancellable=true)
    private void naturality$poseOffset(AvatarRenderState state,org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<net.minecraft.world.phys.Vec3> cir) {
        float inversion=((EndGravityRenderState)state).naturality$inversion();
        if(inversion==0)return;
        var offset=cir.getReturnValue();
        cir.setReturnValue(new net.minecraft.world.phys.Vec3(offset.x,offset.y*Math.cos(Math.PI*inversion),offset.z));
    }
    @Inject(method="extractFlightData",at=@At("TAIL"))
    private void naturality$bank(Avatar entity, AvatarRenderState state, float partial, CallbackInfo ci) {
        state.flyingYRot*=(float)Math.cos(Math.PI*((EndGravityRenderState)state).naturality$inversion());
    }
}