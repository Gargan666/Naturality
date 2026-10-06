package naturality.mixin;

import naturality.weather.EndGravity;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(LivingEntity.class)
public abstract class EndGravityLivingMixin {
    // Run vanilla aerodynamics in the gravity-local frame, then return the
    // result to world space. Lift, pitch and dive conversion all share a frame.
    @WrapMethod(method="updateFallFlyingMovement")
    private Vec3 naturality$invertedGlide(Vec3 movement,Operation<Vec3> original) {
        if(!EndGravity.inverted((LivingEntity)(Object)this))return original.call(movement);
        var local=original.call(new Vec3(movement.x,-movement.y,movement.z));
        return new Vec3(local.x,-local.y,local.z);
    }
    @ModifyExpressionValue(method="updateFallFlyingMovement",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/LivingEntity;getLookAngle()Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 naturality$glideLook(Vec3 look) {
        return EndGravity.inverted((LivingEntity)(Object)this)?new Vec3(look.x,-look.y,look.z):look;
    }
    @ModifyExpressionValue(method="updateFallFlyingMovement",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/LivingEntity;getXRot()F"))
    private float naturality$glidePitch(float pitch) {
        return EndGravity.inverted((LivingEntity)(Object)this)?-pitch:pitch;
    }
    @ModifyExpressionValue(method="updateFallFlyingMovement",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/LivingEntity;getEffectiveGravity()D"))
    private double naturality$glideGravity(double gravity) {
        return EndGravity.inverted((LivingEntity)(Object)this)?-gravity:gravity;
    }
    @Shadow protected abstract float getJumpPower();
    @Inject(method="jumpFromGround",at=@At("HEAD"),cancellable=true)
    private void naturality$ceilingJump(CallbackInfo ci) {
        var e=(LivingEntity)(Object)this;if(!EndGravity.inverted(e))return;
        var v=e.getDeltaMovement();
        e.setDeltaMovement(v.x,Math.min(-getJumpPower(),v.y),v.z);
        if(e.isSprinting()) {
            double angle=Math.toRadians(e.getYRot());
            e.addDeltaMovement(new Vec3(-Math.sin(angle)*.2,0,Math.cos(angle)*.2));
        }
        e.needsSync=true;
        ci.cancel();
    }
    @ModifyVariable(method="travel",at=@At("HEAD"),argsOnly=true)
    private Vec3 naturality$controls(Vec3 input) {
        var e=(LivingEntity)(Object)this;
        return e instanceof Player && EndGravity.inverted(e)?new Vec3(-input.x,input.y,input.z):input;
    }
    @Inject(method="getEffectiveGravity",at=@At("RETURN"),cancellable=true)
    private void naturality$slowFalling(CallbackInfoReturnable<Double> cir) {
        var e=(LivingEntity)(Object)this;
        if(EndGravity.inverted(e) && e.hasEffect(net.minecraft.world.effect.MobEffects.SLOW_FALLING)
                && e.getDeltaMovement().y>=0) cir.setReturnValue(Math.max(e.getGravity(),-.01));
    }
}
