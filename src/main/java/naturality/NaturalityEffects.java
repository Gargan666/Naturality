package naturality;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

public final class NaturalityEffects {
    public static final Holder<MobEffect> DISTORTION = Registry.registerForHolder(
        BuiltInRegistries.MOB_EFFECT, Naturality.id("distortion"), new DistortionEffect());
    private NaturalityEffects() {}
    public static void initialize() {}
    private static final class DistortionEffect extends MobEffect {
        private DistortionEffect() { super(MobEffectCategory.HARMFUL, 0x9565CD); }
    }
}
