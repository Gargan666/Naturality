package naturality;

import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;

public final class NaturalityBlocks {
    public static final Block SMOOTH_ENDSTONE = Blocks.register(
        ResourceKey.create(Registries.BLOCK, Naturality.id("smooth_endstone")),
        BlockBehaviour.Properties.ofFullCopy(Blocks.END_STONE));
    public static final BlockItem SMOOTH_ENDSTONE_ITEM = Registry.register(BuiltInRegistries.ITEM,
        ResourceKey.create(Registries.ITEM, Naturality.id("smooth_endstone")),
        new BlockItem(SMOOTH_ENDSTONE, new Item.Properties()
            .setId(ResourceKey.create(Registries.ITEM, Naturality.id("smooth_endstone")))
            .useBlockDescriptionPrefix()));

    private NaturalityBlocks() {}

    public static final Block LIRESTONE = Blocks.register(
        ResourceKey.create(Registries.BLOCK, Naturality.id("lirestone")),
        BlockBehaviour.Properties.ofFullCopy(Blocks.END_STONE).strength(2.5F, 9.0F));
    public static final BlockItem LIRESTONE_ITEM = Registry.register(BuiltInRegistries.ITEM,
        ResourceKey.create(Registries.ITEM, Naturality.id("lirestone")),
        new BlockItem(LIRESTONE, new Item.Properties()
            .setId(ResourceKey.create(Registries.ITEM, Naturality.id("lirestone")))
            .useBlockDescriptionPrefix()));

    public static void initialize() {
        SMOOTH_ENDSTONE_ITEM.registerBlocks(Item.BY_BLOCK, SMOOTH_ENDSTONE_ITEM);
        LIRESTONE_ITEM.registerBlocks(Item.BY_BLOCK, LIRESTONE_ITEM);
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.BUILDING_BLOCKS)
            .register(output -> output.insertAfter(Blocks.END_STONE, SMOOTH_ENDSTONE, LIRESTONE));
    }
}
