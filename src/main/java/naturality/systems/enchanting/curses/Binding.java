package naturality.systems.enchanting.curses;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;

@org.jspecify.annotations.NullMarked
public final class Binding {
    private Binding() {}
    public static boolean prevents(boolean original, ItemStack armor, DataComponentType<?> effect) {
        return original && !(armor.isBroken() && effect == EnchantmentEffectComponents.PREVENT_ARMOR_CHANGE);
    }
}
