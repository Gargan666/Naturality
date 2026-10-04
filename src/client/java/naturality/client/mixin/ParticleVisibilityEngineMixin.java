package naturality.client.mixin;

import naturality.client.particle.ParticleVisibility;
import naturality.client.particle.FallParticleBudget;
import naturality.client.particle.BorderSplashParticle;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.particles.ParticleOptions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ParticleEngine.class)
public abstract class ParticleVisibilityEngineMixin {
    @SuppressWarnings("null") @Shadow protected ClientLevel level;
    @SuppressWarnings("null") @Shadow private it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap<net.minecraft.core.particles.ParticleLimit> trackedParticleCounts;

    @Inject(method="createParticle",at=@At("HEAD"),cancellable=true)
    private void naturality$skipHiddenSpawn(ParticleOptions options,double x,double y,double z,double vx,double vy,double vz,
            CallbackInfoReturnable<Particle> cir) {
        if((ParticleVisibility.fallType(options) && naturality$fallFull()) || ParticleVisibility.rejectSpawn(level,options,x,y,z)) {
            ParticleVisibility.rejected();cir.setReturnValue(null);
        }
    }
    @Inject(method="createParticle",at=@At("RETURN"),cancellable=true)
    private void naturality$returnRejectedParticle(ParticleOptions options,double x,double y,double z,double vx,double vy,double vz,
            CallbackInfoReturnable<Particle> cir) {
        var particle=cir.getReturnValue();
        // add() already checked the actual sprite bounds; do not repeat that work.
        if(particle!=null && !particle.isAlive())cir.setReturnValue(null);
    }
    @Inject(method="add",at=@At("HEAD"),cancellable=true)
    private void naturality$checkDirectParticle(Particle particle,CallbackInfo ci) {
        boolean fall=particle instanceof BorderSplashParticle;
        if((fall && naturality$fallFull()) || ((ParticleVisibility.Access)particle).naturality$hidden(true)) {
            particle.remove();ParticleVisibility.rejected();ci.cancel();
        } else if(fall) {
            FallParticleBudget.admitted(level);
        }
    }
    private boolean naturality$fallFull() {
        return FallParticleBudget.exhausted(level) || trackedParticleCounts.getInt(FallParticleBudget.LIMIT)>=FallParticleBudget.LIMIT.limit();
    }
    @Inject(method="clearParticles",at=@At("HEAD"))
    private void naturality$resetVisibility(CallbackInfo ci){ParticleVisibility.reset();FallParticleBudget.reset();}
}
