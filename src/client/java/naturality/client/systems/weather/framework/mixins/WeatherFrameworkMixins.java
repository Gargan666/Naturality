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
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import naturality.client.systems.weather.framework.WeatherClient.WeatherParticleContext;
import net.minecraft.client.particle.ParticleEngine;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Pseudo;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleGroup;
import java.util.Queue;
import java.util.Spliterator;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;

/** Feature integration hooks; each nested type retains its independent Mixin target and gate. */
public final class WeatherFrameworkMixins {
    private WeatherFrameworkMixins() {}

    @Mixin(ClientLevel.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class WeatherClientLevelMixin {
        @WrapOperation(method = "tickWeatherEffects", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientLevel;addParticle(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)V"))
        private void naturality$groundImpact(ClientLevel level, ParticleOptions particle, double x, double y, double z,
                double vx, double vy, double vz, Operation<Void> original) {
            var weather = WeatherSystem.state(level);
            if (weather == null || level.getRandom().nextFloat() < weather.groundImpactChance())
                original.call(level,particle,x,y,z,vx,vy,vz);
        }
        @WrapOperation(method = "getPrecipitationAt", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/biome/Biome;getPrecipitationAt(Lnet/minecraft/core/BlockPos;I)Lnet/minecraft/world/level/biome/Biome$Precipitation;"))
        private Biome.Precipitation naturality$precipitation(Biome biome, BlockPos pos, int seaLevel, Operation<Biome.Precipitation> original) {
            return WeatherSystem.precipitation((ClientLevel)(Object)this, biome, original.call(biome, pos, seaLevel));
        }
    }

    @Mixin(ParticleEngine.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class WeatherParticleEngineMixin implements WeatherParticleContext.LevelOwner {
        @Shadow protected @Nullable ClientLevel level;

        @Override public @Nullable ClientLevel naturality$particleLevel() { return level; }

        @WrapMethod(method = "tick")
        private void naturality$shareWeatherQueries(Operation<Void> original) {
            var world = level;
            if (world == null) { original.call(); return; }
            WeatherParticleContext.begin(world);
            try { original.call(); }
            finally { WeatherParticleContext.end(); }
        }
    }

    @Mixin(ParticleGroup.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class WeatherParticleGroupOwnerMixin implements WeatherParticleContext.LevelOwner {
        @SuppressWarnings("null") // Assigned by ParticleGroup's constructor.
        @Shadow @Final protected ParticleEngine engine;

        @Override public @Nullable ClientLevel naturality$particleLevel() {
            return WeatherParticleContext.level(engine);
        }
    }

    // These optional bridges run inside the existing tick tasks. They do not
    // change scheduling or move extraction/GPU work onto particle workers.
    @Mixin(value = ParticleGroup.class, priority = 1100)
    @org.jspecify.annotations.NullMarked
    public static abstract class AsyncParticlesWeatherGroupMixin {
        @WrapMethod(method = "asyncparticles$tickParticles", remap = false, require = 0)
        private void naturality$workerWeather(boolean gpu, Operation<Void> original) {
            var world = WeatherParticleContext.level(this);
            if (world == null) { original.call(gpu); return; }
            WeatherParticleContext.begin(world);
            try { original.call(gpu); }
            finally { WeatherParticleContext.end(); }
        }
    }

    @Pseudo
    @Mixin(targets = "fun.qu_an.minecraft.asyncparticles.client.core.particle.tick.TickParticleRecursiveAction", remap = false)
    @org.jspecify.annotations.NullMarked
    public static abstract class AsyncParticlesWeatherSplitMixin {
        @SuppressWarnings("null") // Assigned by the optional worker's constructor.
        @Shadow @Final private ParticleGroup<?> group;

        @WrapOperation(method = "compute", at = @At(value = "INVOKE",
            target = "Ljava/util/Spliterator;forEachRemaining(Ljava/util/function/Consumer;)V"))
        private void naturality$leafWeather(Spliterator<Particle> particles, Consumer<? super Particle> action,
                Operation<Void> original) {
            var world = WeatherParticleContext.level(group);
            if (world == null) { original.call(particles, action); return; }
            WeatherParticleContext.begin(world);
            try { original.call(particles, action); }
            finally { WeatherParticleContext.end(); }
        }
    }

    // ParticleCore merges this method at priority 100000. A higher priority
    // permits its stream call to be wrapped after that method has been merged.
    @Mixin(value = ParticleEngine.class, priority = 100100)
    @org.jspecify.annotations.NullMarked
    public static abstract class ParticleCoreWeatherStreamMixin {
        // ASYNC can be disabled in ParticleCore's configuration, removing the
        // entire target method; no optional classes appear in this hook's API.
        @WrapOperation(method = "asyncTickParticles", remap = false, require = 0,
            at = @At(value = "INVOKE", target = "Ljava/util/Queue;parallelStream()Ljava/util/stream/Stream;"))
        private Stream<Particle> naturality$parallelWeather(Queue<Particle> particles,
                Operation<Stream<Particle>> original) {
            var stream = original.call(particles);
            var world = WeatherParticleContext.level(this);
            return world == null ? stream : WeatherParticleContext.scopedStream(world, stream);
        }
    }
}
