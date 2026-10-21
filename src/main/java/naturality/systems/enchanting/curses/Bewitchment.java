package naturality.systems.enchanting.curses;

import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;

@org.jspecify.annotations.NullMarked
public final class Bewitchment {
    private Bewitchment() {}
    public static MobEffectInstance extend(LivingEntity wearer, MobEffectInstance effect) {
        int level = CurseLevels.armor(wearer, "bewitchment");
        return level == 0 || effect.getEffect().value().getCategory() != MobEffectCategory.HARMFUL
            ? effect : effect.withScaledDuration(1.0F + 0.5F * level);
    }
}
