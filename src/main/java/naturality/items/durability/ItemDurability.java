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

    public record Repair(ItemStack result, int materials) {}

    public static boolean hasNameChange(ItemStack input, @Nullable String name) {
        return name != null && !StringUtil.isBlank(name)
            ? !name.equals(input.getHoverName().getString()) : input.has(DataComponents.CUSTOM_NAME);
    }

    public static @Nullable Repair repair(ItemStack input, ItemStack material, @Nullable String name) {
        if (input.isEmpty()) return null;
        int count = 0;
        ItemStack result = input.copy();
        if (material.isEmpty()) {
            if (!hasNameChange(input, name)) return null;
        } else {
            if (!input.isDamaged() || !input.isValidRepairItem(material)) return null;
            int perMaterial = Math.max(1, input.getMaxDamage() / 4);
            count = Math.min(material.getCount(), (input.getDamageValue() - 1) / perMaterial + 1);
            result.setDamageValue((int)Math.max(0L, input.getDamageValue() - (long)count * perMaterial));
        }
        if (name != null && !StringUtil.isBlank(name)) {
            if (!name.equals(input.getHoverName().getString())) {
                result.set(DataComponents.CUSTOM_NAME, Component.literal(name));
            }
        } else if (input.has(DataComponents.CUSTOM_NAME)) {
            result.remove(DataComponents.CUSTOM_NAME);
        }
        return new Repair(result, count);
    }
}
