package naturality.blocks.gilded;

import java.util.function.Function;
import naturality.Naturality;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.WoodType;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.storage.loot.providers.number.ints.ContextIntProviders;

/** The supplied Gilded materials retain Nether wood and fungus behavior. */
@org.jspecify.annotations.NullMarked
public final class GildedBlocks {
    private static final ResourceKey<Feature> FUNGUS_GROWTH = ResourceKey.create(Registries.FEATURE, Naturality.id("gilded_fungus_planted"));
    private static final ResourceKey<Feature> NYLIUM_GROWTH = ResourceKey.create(Registries.FEATURE, Naturality.id("gilded_nylium_bonemeal"));
    public static final Block STEM = register("gilded_stem", Blocks.WARPED_STEM, RotatedPillarBlock::new);
    public static final Block HYPHAE = register("gilded_hyphae", Blocks.WARPED_HYPHAE, RotatedPillarBlock::new);
    public static final Block PLANKS = register("gilded_planks", Blocks.WARPED_PLANKS, Block::new);
    public static final Block STAIRS = register("gilded_stairs", Blocks.WARPED_STAIRS, p -> new StairBlock(PLANKS.defaultBlockState(), p));
    public static final Block SLAB = register("gilded_slab", Blocks.WARPED_SLAB, SlabBlock::new);
    public static final Block FENCE = register("gilded_fence", Blocks.WARPED_FENCE, FenceBlock::new);
    public static final Block FENCE_GATE = register("gilded_fence_gate", Blocks.WARPED_FENCE_GATE, p -> new FenceGateBlock(WoodType.WARPED, p));
    public static final Block BUTTON = register("gilded_button", Blocks.WARPED_BUTTON, p -> new ButtonBlock(BlockSetType.WARPED, 30, p));
    public static final Block PRESSURE_PLATE = register("gilded_pressure_plate", Blocks.WARPED_PRESSURE_PLATE, p -> new PressurePlateBlock(BlockSetType.WARPED, p));
    public static final Block NYLIUM = register("gilded_nylium", Blocks.WARPED_NYLIUM, GildedNylium::new);
    public static final Block FUNGUS = register("gilded_fungus", Blocks.WARPED_FUNGUS,
        p -> new NetherFungusBlock(FUNGUS_GROWTH, NYLIUM, BlockTags.SUPPORTS_WARPED_FUNGUS, p));
    public static final Block WART_BLOCK = register("gilded_wart_block", Blocks.WARPED_WART_BLOCK, Block::new);
    public static final Block SHROOMLIGHT = register("gilded_shroomlight", Blocks.SHROOMLIGHT, Block::new);

    private GildedBlocks() { }

    private static Block register(String name, Block original, Function<BlockBehaviour.Properties, Block> factory) {
        float hardness = original.defaultBlockState().getDestroySpeed(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
        Block block = Blocks.register(ResourceKey.create(Registries.BLOCK, Naturality.id(name)), factory,
            BlockBehaviour.Properties.ofFullCopy(original).strength(Math.max(0.1F, hardness * 1.25F),
                Math.max(0.1F, original.getExplosionResistance() * 1.25F)));
        var key = ResourceKey.create(Registries.ITEM, Naturality.id(name));
        var properties = new Item.Properties().setId(key).useBlockDescriptionPrefix();
        // Item holder components are not bound during mod registration; use the native providers directly.
        if (original == Blocks.WARPED_FUNGUS || original == Blocks.SHROOMLIGHT) {
            properties.compostable(ContextIntProviders.COMPOSTABLE_MEDIUM);
        } else if (original == Blocks.WARPED_WART_BLOCK) {
            properties.compostable(ContextIntProviders.COMPOSTABLE_MEDIUM_HIGH);
        }
        var item = Registry.register(BuiltInRegistries.ITEM, key, new BlockItem(block, properties));
        item.registerBlocks(Item.BY_BLOCK, item);
        return block;
    }

    public static void initialize() {
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.BUILDING_BLOCKS).register(output ->
            output.insertAfter(Blocks.WARPED_BUTTON, STEM, HYPHAE, PLANKS, STAIRS, SLAB, FENCE, FENCE_GATE, PRESSURE_PLATE, BUTTON));
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.NATURAL_BLOCKS).register(output -> {
            output.insertAfter(Blocks.WARPED_NYLIUM, NYLIUM);
            output.insertAfter(Blocks.WARPED_FUNGUS, FUNGUS);
            output.insertAfter(Blocks.WARPED_WART_BLOCK, WART_BLOCK);
            output.insertAfter(Blocks.SHROOMLIGHT, SHROOMLIGHT);
        });
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(output ->
            output.insertAfter(Blocks.SHROOMLIGHT, SHROOMLIGHT));
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.REDSTONE_BLOCKS).register(output -> {
            output.insertAfter(Blocks.OAK_BUTTON, BUTTON);
            output.insertAfter(Blocks.OAK_PRESSURE_PLATE, PRESSURE_PLATE);
            output.insertAfter(Blocks.OAK_FENCE_GATE, FENCE_GATE);
        });
    }

    private static final class GildedNylium extends NyliumBlock {
        private GildedNylium(Properties properties) { super(properties); }

        @Override
        public void performBonemeal(ServerLevel level, RandomSource random, BlockPos pos, BlockState state, BonemealSource source) {
            if (level.isInsideBuildHeight(pos.above())) {
                level.registryAccess().lookupOrThrow(Registries.FEATURE).get(NYLIUM_GROWTH).ifPresent(feature ->
                    feature.value().place(level, level.getChunkSource().getGenerator(), random, pos.above()));
            }
        }
    }
}
