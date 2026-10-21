package naturality.systems.enchanting.curses;

import naturality.systems.enchanting.framework.Enchanting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.ItemEnchantments;

/** Placeholder curse: each level adds 25% expected wear after durability protection. */
public final class Fragility {
    private Fragility() {}
    /** Gameplay wear percentage; authored descriptions can express this as {25 * level}. */
    public static int extraWearPercent(int level) { return level * 25; }

    public static int wear(ItemStack item, int wear, RandomSource random) {
        if (wear <= 0) return wear;
        for (var entry : item.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).entrySet()) {
            String id = Enchanting.name(entry.getKey());
            if (!id.equals("naturality:fragility_curse") && !id.equals("naturality:fragility")) continue;
            double extra = wear * (extraWearPercent(entry.getIntValue()) / 100.0);
            long whole = (long)extra;
            return (int)Math.min(Integer.MAX_VALUE, wear + whole + (random.nextDouble() < extra - whole ? 1 : 0));
        }
        return wear;
    }
}
