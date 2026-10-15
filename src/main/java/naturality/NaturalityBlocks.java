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
        BlockBehaviour.Properties.ofFullCopy(Blocks.END_STONE).strength(2.5F, 9.0F).sound(NaturalitySounds.LIRESTONE));
    public static final BlockItem LIRESTONE_ITEM = Registry.register(BuiltInRegistries.ITEM,
        ResourceKey.create(Registries.ITEM, Naturality.id("lirestone")),
        new BlockItem(LIRESTONE, new Item.Properties()
            .setId(ResourceKey.create(Registries.ITEM, Naturality.id("lirestone")))
            .useBlockDescriptionPrefix()));

    public static final Block SMOOTH_LIRESTONE = Blocks.register(
        ResourceKey.create(Registries.BLOCK, Naturality.id("smooth_lirestone")),
        BlockBehaviour.Properties.ofFullCopy(LIRESTONE));
    public static final BlockItem SMOOTH_LIRESTONE_ITEM = Registry.register(BuiltInRegistries.ITEM,
        ResourceKey.create(Registries.ITEM, Naturality.id("smooth_lirestone")),
        new BlockItem(SMOOTH_LIRESTONE, new Item.Properties()
            .setId(ResourceKey.create(Registries.ITEM, Naturality.id("smooth_lirestone")))
            .useBlockDescriptionPrefix()));

    public static final Block CHORUS_SPROUT = Blocks.register(
        ResourceKey.create(Registries.BLOCK, Naturality.id("chorus_sprout")),
        naturality.block.ChorusSproutBlock::new,
        BlockBehaviour.Properties.ofFullCopy(Blocks.SHORT_GRASS).sound(net.minecraft.world.level.block.SoundType.ROOTS));
    public static final BlockItem CHORUS_SPROUT_ITEM = Registry.register(BuiltInRegistries.ITEM,
        ResourceKey.create(Registries.ITEM, Naturality.id("chorus_sprout")),
        new BlockItem(CHORUS_SPROUT, new Item.Properties()
            .setId(ResourceKey.create(Registries.ITEM, Naturality.id("chorus_sprout")))
            .useBlockDescriptionPrefix()));

    public static final Block LYREN_SPROUT = Blocks.register(
        ResourceKey.create(Registries.BLOCK, Naturality.id("lyren_sprout")),
        naturality.block.ChorusSproutBlock::new, BlockBehaviour.Properties.ofFullCopy(CHORUS_SPROUT));
    public static final BlockItem LYREN_SPROUT_ITEM = Registry.register(BuiltInRegistries.ITEM,
        ResourceKey.create(Registries.ITEM, Naturality.id("lyren_sprout")),
        new BlockItem(LYREN_SPROUT, new Item.Properties()
            .setId(ResourceKey.create(Registries.ITEM, Naturality.id("lyren_sprout"))).useBlockDescriptionPrefix()));

    public static final Block LYREN_GROWTH = Blocks.register(
        ResourceKey.create(Registries.BLOCK, Naturality.id("lyren_growth")),
        naturality.block.ChorusSproutBlock::new, BlockBehaviour.Properties.ofFullCopy(CHORUS_SPROUT));
    public static final BlockItem LYREN_GROWTH_ITEM = Registry.register(BuiltInRegistries.ITEM,
        ResourceKey.create(Registries.ITEM, Naturality.id("lyren_growth")),
        new BlockItem(LYREN_GROWTH, new Item.Properties()
            .setId(ResourceKey.create(Registries.ITEM, Naturality.id("lyren_growth"))).useBlockDescriptionPrefix()));

    public static final Block ECHO_CORAL = Blocks.register(
        ResourceKey.create(Registries.BLOCK, Naturality.id("echo_coral")),
        BlockBehaviour.Properties.ofFullCopy(Blocks.BROWN_MUSHROOM_BLOCK).sound(NaturalitySounds.ECHO_CORAL));
    public static final BlockItem ECHO_CORAL_ITEM = Registry.register(BuiltInRegistries.ITEM,
        ResourceKey.create(Registries.ITEM, Naturality.id("echo_coral")),
        new BlockItem(ECHO_CORAL, new Item.Properties()
            .setId(ResourceKey.create(Registries.ITEM, Naturality.id("echo_coral")))
            .useBlockDescriptionPrefix()));

    public static final net.minecraft.world.level.block.SoundType LYREN_HEATH_SOUNDS =
        new net.minecraft.world.level.block.SoundType(1.0F, 1.0F,
            net.minecraft.world.level.block.SoundType.STONE.getBreakSound(),
            Blocks.WARPED_NYLIUM.defaultBlockState().getSoundType().getStepSound(),
            net.minecraft.world.level.block.SoundType.STONE.getPlaceSound(),
            net.minecraft.world.level.block.SoundType.STONE.getHitSound(),
            net.minecraft.world.level.block.SoundType.STONE.getFallSound());

    public static final Block LYREN_HEATH = Blocks.register(
        ResourceKey.create(Registries.BLOCK, Naturality.id("lyren_heath")),
        naturality.block.LyrenHeathBlock::new,
        BlockBehaviour.Properties.ofFullCopy(SMOOTH_ENDSTONE).strength(2.1F, 9.0F)
            .sound(LYREN_HEATH_SOUNDS).randomTicks());
    public static final BlockItem LYREN_HEATH_ITEM = Registry.register(BuiltInRegistries.ITEM,
        ResourceKey.create(Registries.ITEM, Naturality.id("lyren_heath")),
        new BlockItem(LYREN_HEATH, new Item.Properties()
            .setId(ResourceKey.create(Registries.ITEM, Naturality.id("lyren_heath")))
            .useBlockDescriptionPrefix()));

    public static final Block MOSS = Blocks.register(
        ResourceKey.create(Registries.BLOCK, Naturality.id("moss")),
        net.minecraft.world.level.block.GlowLichenBlock::new,
        BlockBehaviour.Properties.ofFullCopy(Blocks.GLOW_LICHEN).lightLevel(_ -> 0)
            .sound(Blocks.MOSS_BLOCK.defaultBlockState().getSoundType()));
    public static final BlockItem MOSS_ITEM = Registry.register(BuiltInRegistries.ITEM,
        ResourceKey.create(Registries.ITEM, Naturality.id("moss")),
        new BlockItem(MOSS, new Item.Properties()
            .setId(ResourceKey.create(Registries.ITEM, Naturality.id("moss")))
            .useBlockDescriptionPrefix()));

    public static final Block CLOVERS = Blocks.register(
        ResourceKey.create(Registries.BLOCK, Naturality.id("clovers")),
        properties -> new net.minecraft.world.level.block.FlowerBedBlock(properties, 3),
        BlockBehaviour.Properties.ofFullCopy(Blocks.WILDFLOWERS));
    public static final BlockItem CLOVERS_ITEM = Registry.register(BuiltInRegistries.ITEM,
        ResourceKey.create(Registries.ITEM, Naturality.id("clovers")),
        new BlockItem(CLOVERS, new Item.Properties()
            .setId(ResourceKey.create(Registries.ITEM, Naturality.id("clovers")))
            .useBlockDescriptionPrefix()));

    public static void initialize() {
        CLOVERS_ITEM.registerBlocks(Item.BY_BLOCK, CLOVERS_ITEM);
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.NATURAL_BLOCKS)
            .register(output -> output.insertAfter(Blocks.WILDFLOWERS, CLOVERS));
        MOSS_ITEM.registerBlocks(Item.BY_BLOCK, MOSS_ITEM);
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.NATURAL_BLOCKS)
            .register(output -> output.insertAfter(Blocks.GLOW_LICHEN, MOSS));
        LYREN_HEATH_ITEM.registerBlocks(Item.BY_BLOCK, LYREN_HEATH_ITEM);
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.NATURAL_BLOCKS)
            .register(output -> output.insertAfter(Blocks.END_STONE, LYREN_HEATH));
        ECHO_CORAL_ITEM.registerBlocks(Item.BY_BLOCK, ECHO_CORAL_ITEM);
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.NATURAL_BLOCKS)
            .register(output -> output.insertAfter(Blocks.BROWN_MUSHROOM_BLOCK, ECHO_CORAL));
        CHORUS_SPROUT_ITEM.registerBlocks(Item.BY_BLOCK, CHORUS_SPROUT_ITEM);
        LYREN_SPROUT_ITEM.registerBlocks(Item.BY_BLOCK, LYREN_SPROUT_ITEM);
        LYREN_GROWTH_ITEM.registerBlocks(Item.BY_BLOCK, LYREN_GROWTH_ITEM);
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.NATURAL_BLOCKS)
            .register(output -> output.insertAfter(Blocks.SHORT_GRASS, CHORUS_SPROUT, LYREN_SPROUT, LYREN_GROWTH));
        SMOOTH_ENDSTONE_ITEM.registerBlocks(Item.BY_BLOCK, SMOOTH_ENDSTONE_ITEM);
        LIRESTONE_ITEM.registerBlocks(Item.BY_BLOCK, LIRESTONE_ITEM);
        SMOOTH_LIRESTONE_ITEM.registerBlocks(Item.BY_BLOCK, SMOOTH_LIRESTONE_ITEM);
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.BUILDING_BLOCKS)
            .register(output -> output.insertAfter(Blocks.END_STONE, SMOOTH_ENDSTONE, LIRESTONE, SMOOTH_LIRESTONE));
    }
}
