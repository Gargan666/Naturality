package naturality;

import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;

public final class NaturalityParticles {
    public static final SimpleParticleType RAIN_CLUSTER=Registry.register(BuiltInRegistries.PARTICLE_TYPE,
        Naturality.id("rain_cluster"),FabricParticleTypes.simple());
    public static final SimpleParticleType SNOW_CLUSTER=Registry.register(BuiltInRegistries.PARTICLE_TYPE,
        Naturality.id("snow_cluster"),FabricParticleTypes.simple());
    public static final SimpleParticleType WATERFALL=Registry.register(BuiltInRegistries.PARTICLE_TYPE,
        Naturality.id("waterfall"),FabricParticleTypes.simple());
    public static final SimpleParticleType WATERFALL_BIG=Registry.register(BuiltInRegistries.PARTICLE_TYPE,
        Naturality.id("waterfall_big"),FabricParticleTypes.simple());
    public static final SimpleParticleType LAVA_SPLASH=Registry.register(BuiltInRegistries.PARTICLE_TYPE,
        Naturality.id("lava_splash"),FabricParticleTypes.simple());
    public static final SimpleParticleType LAVAFALL=Registry.register(BuiltInRegistries.PARTICLE_TYPE,
        Naturality.id("lavafall"),FabricParticleTypes.simple());
    public static final SimpleParticleType LAVAFALL_BIG=Registry.register(BuiltInRegistries.PARTICLE_TYPE,
        Naturality.id("lavafall_big"),FabricParticleTypes.simple());
    public static final SimpleParticleType STAR_TRAIL=Registry.register(BuiltInRegistries.PARTICLE_TYPE,
        Naturality.id("star_trail"),FabricParticleTypes.simple());
    public static void initialize() {}
    private NaturalityParticles() {}
}


