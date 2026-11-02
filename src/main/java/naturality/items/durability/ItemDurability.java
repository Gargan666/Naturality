package naturality.items.durability;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.StringUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/** Exhausted equipment keeps its identity, enchantments and repair history. */
@org.jspecify.annotations.NullMarked
public final class ItemDurability {
    private ItemDurability() {}

    public record Repair(ItemStack result, int materials) {}

    public static boolean isValidRepairMaterial(ItemStack input, ItemStack material, boolean original) {
        if (!input.isDamageableItem()) return original;
        if (input.is(Items.NETHERITE_SWORD) || input.is(Items.NETHERITE_PICKAXE)
                || input.is(Items.NETHERITE_AXE) || input.is(Items.NETHERITE_SHOVEL)
                || input.is(Items.NETHERITE_HOE) || input.is(Items.NETHERITE_SPEAR)
                || input.is(Items.NETHERITE_HELMET) || input.is(Items.NETHERITE_CHESTPLATE)
                || input.is(Items.NETHERITE_LEGGINGS) || input.is(Items.NETHERITE_BOOTS)
                || input.is(Items.NETHERITE_HORSE_ARMOR) || input.is(Items.NETHERITE_NAUTILUS_ARMOR)) {
            // Replace only the ingot; preserve any other data-pack repair materials.
            return material.is(Items.NETHERITE_SCRAP) || (original && !material.is(Items.NETHERITE_INGOT));
        }
        if (original) return true;
        // Existing material repair components remain authoritative for other equipment.
        if (input.has(DataComponents.REPAIRABLE)) return false;
        if (input.is(Items.SHEARS)) return material.is(Items.IRON_INGOT);
        if (input.is(Items.BOW) || input.is(Items.CROSSBOW) || input.is(Items.FISHING_ROD)) {
            return material.is(Items.STRING) || material.is(Items.STICK);
        }
        if (input.is(Items.CARROT_ON_A_STICK)) {
            return material.is(Items.STRING) || material.is(Items.STICK) || material.is(Items.CARROT);
        }
        if (input.is(Items.WARPED_FUNGUS_ON_A_STICK)) {
            return material.is(Items.STRING) || material.is(Items.STICK) || material.is(Items.WARPED_FUNGUS);
        }
        if (input.is(Items.FLINT_AND_STEEL)) return material.is(Items.IRON_INGOT) || material.is(Items.FLINT);
        if (input.is(Items.BRUSH)) return material.is(Items.COPPER_INGOT) || material.is(Items.FEATHER);
        if (input.is(Items.TRIDENT)) return material.is(Items.PRISMARINE_SHARD);
        return false;
    }

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
