package naturality.client.mixin;

import naturality.client.particle.WaterParticleTint;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.particles.ParticleOptions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ParticleEngine.class)
public abstract class WaterParticleTypeMixin {
    @Inject(method="makeParticle",at=@At("RETURN"))
    private void naturality$markWater(ParticleOptions options,double x,double y,double z,double vx,double vy,double vz,
                                     CallbackInfoReturnable<Particle> cir) {
        naturality.client.particle.WindParticleControl.mark(cir.getReturnValue(),
            net.minecraft.core.registries.BuiltInRegistries.PARTICLE_TYPE.wrapAsHolder(options.getType())
                .is(naturality.NaturalityTags.NO_WIND_EFFECT));
        if(options.getType()==net.minecraft.core.particles.ParticleTypes.SPLASH && cir.getReturnValue() instanceof naturality.client.particle.ImpactDroplet droplet)
            droplet.naturality$waterDroplet();
        if(options.getType()==net.minecraft.core.particles.ParticleTypes.RAIN && cir.getReturnValue() instanceof naturality.client.particle.ImpactDroplet rain)
            rain.naturality$rainSplash();
        if(cir.getReturnValue() instanceof WaterParticleTint.Access particle) {
            particle.naturality$waterParticle(WaterParticleTint.includes(options.getType()));
            particle.naturality$rainParticle(options.getType()==net.minecraft.core.particles.ParticleTypes.RAIN);
        }
    }
}

