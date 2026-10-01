package naturality.client.mixin;

import naturality.weather.WeatherSystem;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;

@Mixin(ClientLevel.class)
public abstract class WeatherClientLevelMixin {
    @Redirect(method = "tickWeatherEffects", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientLevel;addParticle(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)V"))
    private void naturality$groundImpact(ClientLevel level, ParticleOptions particle, double x, double y, double z, double vx, double vy, double vz) {
        var weather = WeatherSystem.state(level);
        if (weather == null || level.getRandom().nextFloat() < weather.groundImpactChance())
            level.addParticle(particle,x,y,z,vx,vy,vz);
    }
    @Redirect(method = "getPrecipitationAt", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/biome/Biome;getPrecipitationAt(Lnet/minecraft/core/BlockPos;I)Lnet/minecraft/world/level/biome/Biome$Precipitation;"))
    private Biome.Precipitation naturality$precipitation(Biome biome, BlockPos pos, int seaLevel) {
        return WeatherSystem.precipitation((ClientLevel)(Object)this, biome, pos);
    }
}
