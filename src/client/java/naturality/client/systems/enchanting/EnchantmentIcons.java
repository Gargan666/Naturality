package naturality.client.systems.enchanting;

import naturality.Naturality;
import naturality.systems.enchanting.framework.Enchanting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;

/** Resource-pack-aware icon selection shared by books, tooltips and the table. */
@org.jspecify.annotations.NullMarked
public final class EnchantmentIcons {
    public static final Identifier MISSING = Naturality.id("textures/misc/enchantment/icons/missing.png");

    private EnchantmentIcons() {}

    public static Identifier texture(Holder<Enchantment> enchantment, boolean encrypted) {
        if (encrypted) return MISSING;
        var key = enchantment.unwrapKey();
        if (key.isEmpty()) return MISSING;
        var id = key.get().identifier();
        String name = id.getPath();
        if (name.endsWith("_curse")) name = name.substring(0, name.length() - "_curse".length());
        String path = "textures/misc/enchantment/icons/" + name + ".png";
        var resources = Minecraft.getInstance().getResourceManager();
        // Namespace folders can disambiguate two mods' enchantments with the same name.
        var qualified = Naturality.id("textures/misc/enchantment/icons/" + id.getNamespace() + "/" + name + ".png");
        if (resources.getResource(qualified).isPresent()) return qualified;
        var local = Naturality.id(path);
        if (resources.getResource(local).isPresent()) return local;
        var source = Identifier.fromNamespaceAndPath(id.getNamespace(), path);
        return resources.getResource(source).isPresent() ? source : MISSING;
    }

    public static Identifier book(ItemStack book) {
        return EnchantingClient.bookTooltip(book).keySet().stream()
            .sorted(java.util.Comparator.<Holder<Enchantment>, Boolean>comparing(h -> h.is(EnchantmentTags.CURSE))
                .thenComparing(Enchanting::name))
            .findFirst().map(h -> texture(h, !book.getOrDefault(Enchanting.REVEALED, false))).orElse(MISSING);
    }
}
