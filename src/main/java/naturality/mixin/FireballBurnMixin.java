package naturality.mixin;

import net.minecraft.world.entity.projectile.hurtingprojectile.AbstractHurtingProjectile;
import net.minecraft.world.entity.projectile.hurtingprojectile.Fireball;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.core.particles.ParticleTypes;

@Mixin(AbstractHurtingProjectile.class)
public abstract class FireballBurnMixin {
    @Inject(method = "shouldBurn", at = @At("HEAD"), cancellable = true)
    private void naturality$modelFire(CallbackInfoReturnable<Boolean> ci) {
        if ((Object)this instanceof Fireball) ci.setReturnValue(false);
    }

    @Inject(method = "createParticleTrail", at = @At("HEAD"), cancellable = true)
    private void naturality$thickSmoke(CallbackInfo ci) {
        if (!((Object)this instanceof Fireball ball)) return;
        ci.cancel();
        if (!ball.level().isClientSide()) return;
        var movement = ball.getDeltaMovement();
        double spread = ball.getBbWidth() * .2;
        // Four large puffs per tick, distributed along the last movement segment.
        for (int i = 0; i < 4; i++) {
            double t = i / 4.0;
            double x = ball.getX() - movement.x * t + (ball.level().getRandom().nextDouble() - .5) * spread;
            double y = ball.getY() + ball.getBbHeight() * .5 - movement.y * t + (ball.level().getRandom().nextDouble() - .5) * spread;
            double z = ball.getZ() - movement.z * t + (ball.level().getRandom().nextDouble() - .5) * spread;
            ball.level().addParticle(ParticleTypes.LARGE_SMOKE, x, y, z, 0, .01, 0);
        }
    }
}

