package naturality.client.systems.weather.nether.ash.mixins;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import naturality.client.systems.weather.nether.ash.AshWeather;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import naturality.worldgen.nether.basaltdeltas.IndustrialDeltas;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Only biome ambient ash is replaced; fire ash and other particle sources remain independent. */
public final class AshWeatherMixins {
    private AshWeatherMixins() {}

    @Mixin(ClientLevel.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class AmbientAshMixin {
        @WrapWithCondition(method = "doAnimateTick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/multiplayer/ClientLevel;addParticle(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)V"))
        private boolean naturality$replaceAsh(ClientLevel level, ParticleOptions particle,
                double x, double y, double z, double dx, double dy, double dz) {
            return particle.getType() != ParticleTypes.WHITE_ASH || !AshWeather.enabled(level)
                || !IndustrialDeltas.isDelta(level.getBiome(BlockPos.containing(x,y,z)));
        }
    }
}
