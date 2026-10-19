package naturality.items.durability;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.StringUtil;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/** Exhausted equipment keeps its identity, enchantments and repair history. */
@org.jspecify.annotations.NullMarked
public final class ItemDurability {
    private ItemDurability() {}

    public record Repair(ItemStack result, int materials, int levels) {}

    public static @Nullable Repair repair(ItemStack input, ItemStack material, @Nullable String name) {
        if (!input.isDamaged() || material.isEmpty() || !input.isValidRepairItem(material)) return null;
        int perMaterial = Math.max(1, input.getMaxDamage() / 4);
        int count = Math.min(material.getCount(), (input.getDamageValue() - 1) / perMaterial + 1);
        ItemStack result = input.copy();
        result.setDamageValue((int)Math.max(0L, input.getDamageValue() - (long)count * perMaterial));
        int levels = 0;
        if (name != null && !StringUtil.isBlank(name)) {
            if (!name.equals(input.getHoverName().getString())) {
                result.set(DataComponents.CUSTOM_NAME, Component.literal(name));
                levels = 1;
            }
        } else if (input.has(DataComponents.CUSTOM_NAME)) {
            result.remove(DataComponents.CUSTOM_NAME);
            levels = 1;
        }
        return new Repair(result, count, levels);
    }
}
