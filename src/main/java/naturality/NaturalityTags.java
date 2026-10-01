package naturality;

import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.core.particles.ParticleType;

public final class NaturalityTags {
    public static final TagKey<ParticleType<?>> NO_WIND_EFFECT =
        TagKey.create(Registries.PARTICLE_TYPE, Naturality.id("no_wind_effect"));
    private NaturalityTags() {}
}
