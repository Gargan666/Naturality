package naturality.mixin;

import naturality.NaturalityParticles;
import naturality.config.Feature;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(EnderDragon.class)
public abstract class DragonDeathExplosionMixin {
    @Redirect(method = {"aiStep", "tickDeath"}, at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/level/Level;addParticle(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)V"))
    private void naturality$endDeathBurst(Level level, ParticleOptions particle,
            double x, double y, double z, double vx, double vy, double vz) {
        if (Feature.EXPLOSION_PARTICLES.enabled()
                && (particle.getType() == ParticleTypes.EXPLOSION || particle.getType() == ParticleTypes.EXPLOSION_EMITTER)) {
            particle = NaturalityParticles.END_EXPLOSION;
        }
        level.addParticle(particle, x, y, z, vx, vy, vz);
    }
}
