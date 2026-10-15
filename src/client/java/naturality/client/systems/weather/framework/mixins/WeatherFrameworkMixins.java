package naturality.client.systems.weather.framework.mixins;

import naturality.systems.weather.framework.WeatherSystem;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import naturality.client.systems.weather.framework.WeatherClient.WeatherParticleContext;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.state.level.ParticlesRenderState;
import org.spongepowered.asm.mixin.Shadow;

/** Feature integration hooks; each nested type retains its independent Mixin target and gate. */
public final class WeatherFrameworkMixins {
    private WeatherFrameworkMixins() {}

    @Mixin(ClientLevel.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class WeatherClientLevelMixin {
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

    @Mixin(ParticleEngine.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class WeatherParticleEngineMixin {
        // Initialized by ParticleEngine; mixin shadow fields have no constructor assignment.
        @SuppressWarnings("null") @Shadow protected ClientLevel level;

        @WrapMethod(method = "tick")
        private void naturality$shareWeatherQueries(Operation<Void> original) {
            WeatherParticleContext.begin(level);
            try { original.call(); }
            finally { WeatherParticleContext.end(); }
        }
        @WrapMethod(method="extract")
        private void naturality$shareRenderQueries(ParticlesRenderState output,Frustum frustum,Camera camera,float partial,Operation<Void> original) {
            WeatherParticleContext.begin(level);
            try {original.call(output,frustum,camera,partial);}
            finally {WeatherParticleContext.end();}
        }
    }
}
