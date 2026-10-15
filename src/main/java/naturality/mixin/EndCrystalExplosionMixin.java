package naturality.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import naturality.NaturalityParticles;
import naturality.config.Feature;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Identify crystal explosions before their source is discarded on the client. */
@Mixin(ServerLevel.class)
public abstract class EndCrystalExplosionMixin {
    @ModifyVariable(method = "explode", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private ParticleOptions naturality$smallCrystalExplosion(ParticleOptions particle, @Local(argsOnly = true) @Nullable Entity source) {
        return (source instanceof EndCrystal || source == null && naturality.particle.EndExplosionContext.active())
            && Feature.EXPLOSION_PARTICLES.enabled() && particle.getType() == ParticleTypes.EXPLOSION
            ? NaturalityParticles.END_EXPLOSION : particle;
    }

    @ModifyVariable(method = "explode", at = @At("HEAD"), argsOnly = true, ordinal = 1)
    private ParticleOptions naturality$largeCrystalExplosion(ParticleOptions particle, @Local(argsOnly = true) @Nullable Entity source) {
        return (source instanceof EndCrystal || source == null && naturality.particle.EndExplosionContext.active())
            && Feature.EXPLOSION_PARTICLES.enabled() && particle.getType() == ParticleTypes.EXPLOSION_EMITTER
            ? NaturalityParticles.END_EXPLOSION : particle;
    }
}
