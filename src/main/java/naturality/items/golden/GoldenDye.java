package naturality.items.golden;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import naturality.Naturality;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.*;

import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.storage.loot.providers.number.ints.ContextIntProviders;
import org.jspecify.annotations.Nullable;

/** The seventeenth dye uses the native dye component, codecs and interactions. */
@org.jspecify.annotations.NullMarked
public final class GoldenDye {
    public static final int RGB = 0xF9BE3A;
    public static final Map<String, Block> BLOCKS = new LinkedHashMap<>();
    public static final Map<String, Item> ITEMS = new LinkedHashMap<>();
    public static net.minecraft.core.Holder<net.minecraft.world.level.saveddata.maps.MapDecorationType> bannerMarker() {
        return BuiltInRegistries.MAP_DECORATION_TYPE.getOrThrow(ResourceKey.create(Registries.MAP_DECORATION_TYPE, Naturality.id("banner_golden")));
    }

    private GoldenDye() {}

    public static DyeColor color() { return DyeColor.byId(16); }
    public static boolean isGolden(DyeColor color) { return color.getId() == 16; }

    /** Extra slot on vanilla's fixed-size collection; generic type is preserved at every call site. */
    public interface Extension<T> {
        @Nullable T naturality$golden();
        void naturality$golden(T value);
    }

    @SuppressWarnings("unchecked") // The mixin implements Extension<T> on the same ColorCollection<T> instance.
    public static <T> Extension<T> extension(ColorCollection<T> collection) {
        return (Extension<T>) Extension.class.cast(collection);
    }

    private static Block block(String suffix, ColorCollection<Block> collection, Function<BlockBehaviour.Properties, Block> factory) {
        String name = "golden_" + suffix;
        String lootName = suffix.equals("wall_banner") ? "golden_banner" : name;
        var properties = BlockBehaviour.Properties.ofFullCopy(collection.white()).mapColor(color().getMapColor())
            .overrideLootTable(java.util.Optional.of(ResourceKey.create(Registries.LOOT_TABLE, Naturality.id("blocks/" + lootName))))
            .overrideDescription("block.naturality." + lootName);
        if (suffix.equals("bed")) properties.fallDistanceReduction(0.5F);
        Block block = Blocks.register(ResourceKey.create(Registries.BLOCK, Naturality.id(name)), factory, properties);
        BLOCKS.put(name, block);
        extension(collection).naturality$golden(block);
        var flammables = net.fabricmc.fabric.api.registry.FlammableBlockRegistry.getDefaultInstance();
        var fire = flammables.get(collection.white());
        if (fire != null) flammables.add(block, fire);
        return block;
    }

    private static Item item(String suffix, ColorCollection<Item> collection, Function<Item.Properties, Item> factory, Item.Properties properties) {
        String name = "golden_" + suffix;
        var key = ResourceKey.create(Registries.ITEM, Naturality.id(name));
        Item item = Registry.register(BuiltInRegistries.ITEM, key, factory.apply(properties.setId(key)));
        ITEMS.put(name, item);
        extension(collection).naturality$golden(item);
        if (item instanceof BlockItem blockItem) blockItem.registerBlocks(Item.BY_BLOCK, item);
        return item;
    }

    private static void blockItem(String suffix, ColorCollection<Item> collection, Block block, Item.Properties properties) {
        item(suffix, collection, p -> new BlockItem(block, p), properties.useBlockDescriptionPrefix());
    }

    public static void initialize() {
        var color = color();
        Block wool = block("wool", Blocks.WOOL, Block::new);
        blockItem("wool", Items.WOOL, wool, new Item.Properties().cookingFuel(ContextIntProviders.COOKING_TIME_WOOL));
        blockItem("wool_stairs", Items.WOOL_STAIRS, block("wool_stairs", Blocks.WOOL_STAIRS, p -> new StairBlock(wool.defaultBlockState(), p)), new Item.Properties().cookingFuel(ContextIntProviders.COOKING_TIME_WOOL));
        blockItem("wool_slab", Items.WOOL_SLAB, block("wool_slab", Blocks.WOOL_SLAB, SlabBlock::new), new Item.Properties().cookingFuel(ContextIntProviders.COOKING_TIME_WOOL_SLABS));
        blockItem("carpet", Items.CARPET, block("carpet", Blocks.CARPET, p -> new WoolCarpetBlock(color, p)),
            new Item.Properties().component(DataComponents.EQUIPPABLE, Equippable.llamaSwag(color)).cookingFuel(ContextIntProviders.COOKING_TIME_WOOL_CARPETS));
        blockItem("terracotta", Items.DYED_TERRACOTTA, block("terracotta", Blocks.DYED_TERRACOTTA, Block::new), new Item.Properties());
        blockItem("glazed_terracotta", Items.GLAZED_TERRACOTTA, block("glazed_terracotta", Blocks.GLAZED_TERRACOTTA, GlazedTerracottaBlock::new), new Item.Properties());
        blockItem("stained_glass", Items.STAINED_GLASS, block("stained_glass", Blocks.STAINED_GLASS, p -> new StainedGlassBlock(color, p)), new Item.Properties());
        blockItem("stained_glass_pane", Items.STAINED_GLASS_PANE, block("stained_glass_pane", Blocks.STAINED_GLASS_PANE, p -> new StainedGlassPaneBlock(color, p)), new Item.Properties());
        Block concrete = block("concrete", Blocks.CONCRETE, Block::new);
        blockItem("concrete", Items.CONCRETE, concrete, new Item.Properties());
        blockItem("concrete_stairs", Items.CONCRETE_STAIRS, block("concrete_stairs", Blocks.CONCRETE_STAIRS, p -> new StairBlock(concrete.defaultBlockState(), p)), new Item.Properties());
        blockItem("concrete_slab", Items.CONCRETE_SLAB, block("concrete_slab", Blocks.CONCRETE_SLAB, SlabBlock::new), new Item.Properties());
        blockItem("concrete_powder", Items.CONCRETE_POWDER, block("concrete_powder", Blocks.CONCRETE_POWDER, p -> new ConcretePowderBlock(concrete, p)), new Item.Properties());
        blockItem("bed", Items.BED, block("bed", Blocks.BED, p -> new BedBlock(color, p)), new Item.Properties().stacksTo(1));
        Block candle = block("candle", Blocks.DYED_CANDLE, CandleBlock::new);
        blockItem("candle", Items.DYED_CANDLE, candle, new Item.Properties());
        block("candle_cake", Blocks.DYED_CANDLE_CAKE, p -> new CandleCakeBlock(candle, p));
        Block banner = block("banner", Blocks.BANNER, p -> new BannerBlock(color, p));
        Block wallBanner = block("wall_banner", Blocks.WALL_BANNER, p -> new WallBannerBlock(color, p));
        item("banner", Items.BANNER, p -> new BannerItem(banner, wallBanner, p), new Item.Properties().useBlockDescriptionPrefix().stacksTo(16).cookingFuel(ContextIntProviders.COOKING_TIME_WOOD_BLOCKS)
            .component(DataComponents.BANNER_PATTERNS, net.minecraft.world.level.block.entity.BannerPatternLayers.EMPTY));
        blockItem("shulker_box", Items.DYED_SHULKER_BOX, block("shulker_box", Blocks.DYED_SHULKER_BOX, p -> new ShulkerBoxBlock(color, p)),
            new Item.Properties().stacksTo(1).component(DataComponents.CONTAINER, ItemContainerContents.EMPTY));
        item("dye", Items.DYE, DyeItem::new, new Item.Properties().component(DataComponents.DYE, color));
        item("bundle", Items.DYED_BUNDLE, BundleItem::new, new Item.Properties().stacksTo(1).component(DataComponents.BUNDLE_CONTENTS, BundleContents.EMPTY));
        item("cushion", Items.CUSHION, CushionItem::new, new Item.Properties().stacksTo(16).component(DataComponents.CUSHION_COLOR, color).cookingFuel(ContextIntProviders.COOKING_TIME_WOOL_SLABS));
        item("harness", Items.HARNESS, Item::new, new Item.Properties().stacksTo(1).component(DataComponents.EQUIPPABLE, Equippable.harness(color)));
        Registry.registerForHolder(BuiltInRegistries.MAP_DECORATION_TYPE,
            ResourceKey.create(Registries.MAP_DECORATION_TYPE, Naturality.id("banner_golden")),
            new net.minecraft.world.level.saveddata.maps.MapDecorationType(Naturality.id("golden_banner"), true, true));
    }
}
