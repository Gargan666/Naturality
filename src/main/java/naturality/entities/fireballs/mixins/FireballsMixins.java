package naturality.entities.fireballs.mixins;

import net.minecraft.world.entity.projectile.hurtingprojectile.AbstractHurtingProjectile;
import net.minecraft.world.entity.projectile.hurtingprojectile.Fireball;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.core.particles.ParticleTypes;
import naturality.entities.fireballs.FireballImpactPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.projectile.hurtingprojectile.LargeFireball;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/** Feature integration hooks; each nested type retains its independent Mixin target and gate. */
public final class FireballsMixins {
    private FireballsMixins() {}

    @Mixin(AbstractHurtingProjectile.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class FireballBurnMixin {
        @Inject(method = "shouldBurn", at = @At("HEAD"), cancellable = true)
        private void naturality$modelFire(CallbackInfoReturnable<Boolean> ci) {
            var entity = (AbstractHurtingProjectile)(Object)this;
            if (!(entity.level().isClientSide() ? naturality.config.startup.FeatureConfig.Feature.FIREBALL_MODELS.enabled()
                    : naturality.config.startup.FeatureConfig.Feature.FIREBALL_FIRE_SUPPRESSION.enabled())) return;
            if ((Object)this instanceof Fireball) ci.setReturnValue(false);
        }

        @Inject(method = "createParticleTrail", at = @At("HEAD"), cancellable = true)
        private void naturality$thickSmoke(CallbackInfo ci) {
            if (!naturality.config.startup.FeatureConfig.Feature.FIREBALL_MODELS.enabled()) return;
            var ball = (AbstractHurtingProjectile)(Object)this;
            if (!(ball instanceof Fireball) && !(ball instanceof net.minecraft.world.entity.projectile.hurtingprojectile.DragonFireball)) return;
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

    @Mixin(LargeFireball.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class FireballImpactMixin {
        @Inject(method = "onHit", at = @At("HEAD"))
        private void naturality$impactFace(HitResult hit, CallbackInfo ci) {
            var fireball = (LargeFireball) (Object) this;
            if (!(hit instanceof BlockHitResult blockHit) || !(fireball.level() instanceof ServerLevel level)) return;
            var location = blockHit.getLocation();
            var payload = new FireballImpactPayload(level.dimension().identifier(), fireball.getX(), fireball.getY(),
                fireball.getZ(), location.x, location.y, location.z, blockHit.getDirection());
            for (var player : level.players()) {
                if (player.distanceToSqr(fireball) < 64 * 64 && ServerPlayNetworking.canSend(player, FireballImpactPayload.TYPE)) {
                    ServerPlayNetworking.send(player, payload);
                }
            }
        }
    }
}
