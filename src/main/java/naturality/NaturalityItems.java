package naturality;

import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

public final class NaturalityItems {
    public static final Item FOUR_LEAF_CLOVER = Registry.register(BuiltInRegistries.ITEM,
        ResourceKey.create(Registries.ITEM, Naturality.id("four_leaf_clover")),
        new Item(new Item.Properties().setId(ResourceKey.create(Registries.ITEM, Naturality.id("four_leaf_clover")))));

    private NaturalityItems() {}

    public static void initialize() {
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.INGREDIENTS)
            .register(output -> output.insertAfter(Items.RABBIT_FOOT, FOUR_LEAF_CLOVER));
    }
}
