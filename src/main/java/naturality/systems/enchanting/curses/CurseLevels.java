package naturality.systems.enchanting.curses;

import naturality.systems.enchanting.framework.Enchanting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemInstance;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import org.jspecify.annotations.Nullable;

/** Shared lookup for curse hooks; exhausted equipment contributes no active effects. */
@org.jspecify.annotations.NullMarked
public final class CurseLevels {
    private CurseLevels() {}
    private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    public static int stored(@Nullable ItemInstance stack, String name) {
        if (stack == null || stack.count() == 0) return 0;
        for (var entry : stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).entrySet()) {
            if (Enchanting.name(entry.getKey()).equals("naturality:" + name + "_curse")) {
                return Math.min(entry.getIntValue(), entry.getKey().value().getMaxLevel());
            }
        }
        return 0;
    }

    public static int active(@Nullable ItemInstance stack, String name) {
        if (stack == null) return 0;
        int maximum = stack.getOrDefault(DataComponents.MAX_DAMAGE, 0);
        boolean broken = stack instanceof ItemStack item ? item.isBroken()
            : maximum > 0 && stack.get(DataComponents.UNBREAKABLE) == null
                && stack.getOrDefault(DataComponents.DAMAGE, 0) >= maximum;
        return broken ? 0 : stored(stack, name);
    }

    public static int armor(LivingEntity wearer, String name) {
        int total = 0;
        for (var slot : ARMOR) {
            total += active(wearer.getItemBySlot(slot), name);
        }
        return total;
    }
}
