package naturality.systems.enchanting.curses;

import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/** Reduces base weapon damage before enchantment bonuses and critical multipliers. */
@NullMarked
public final class Weakness {
    private Weakness() {}

    public static float damage(@Nullable ItemStack weapon, float baseDamage) {
        return Math.max(0.0F, baseDamage * multiplier(CurseLevels.active(weapon, "weakness")));
    }

    public static float multiplier(int level) {
        return 1.0F - 0.1F * Math.clamp(level, 0, 5);
    }
}
