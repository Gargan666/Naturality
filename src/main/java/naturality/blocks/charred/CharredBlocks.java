package naturality.blocks.charred;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import naturality.Naturality;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.registry.FlammableBlockRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.WoodType;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/** Burned remains reuse native block behavior and the supplied charred textures. */
@NullMarked
public final class CharredBlocks {
    private static final Map<Block, Block> REPLACEMENTS = new LinkedHashMap<>();
    private static final Map<String, Block> REGISTERED = new LinkedHashMap<>();
    public static final Block LOG = register("charred_log", Blocks.OAK_LOG, RotatedPillarBlock::new);
    public static final Block PLANKS = register("charred_planks", Blocks.OAK_PLANKS, Block::new);
    public static final Block STAIRS = register("charred_stairs", Blocks.OAK_STAIRS, p -> new StairBlock(PLANKS.defaultBlockState(), p));
    public static final Block SLAB = register("charred_slab", Blocks.OAK_SLAB, SlabBlock::new);
    public static final Block FENCE = register("charred_fence", Blocks.OAK_FENCE, FenceBlock::new);
    public static final Block GATE = register("charred_fence_gate", Blocks.OAK_FENCE_GATE, p -> new FenceGateBlock(WoodType.OAK, p));
    public static final Block BUTTON = register("charred_button", Blocks.OAK_BUTTON, p -> new ButtonBlock(BlockSetType.OAK, 30, p));
    public static final Block PRESSURE_PLATE = register("charred_pressure_plate", Blocks.OAK_PRESSURE_PLATE, p -> new PressurePlateBlock(BlockSetType.OAK, p));
    public static final Block GRASS = register("charred_grass_block", Blocks.GRASS_BLOCK, ScorchedGrass::new);

    private CharredBlocks() { }

    private static Block register(String name, Block original, Function<BlockBehaviour.Properties, Block> factory) {
        Block block = Blocks.register(ResourceKey.create(Registries.BLOCK, Naturality.id(name)), factory,
            BlockBehaviour.Properties.ofFullCopy(original));
        var key = ResourceKey.create(Registries.ITEM, Naturality.id(name));
        var item = Registry.register(BuiltInRegistries.ITEM, key,
            new BlockItem(block, new Item.Properties().setId(key).useBlockDescriptionPrefix()));
        item.registerBlocks(Item.BY_BLOCK, item);
        REGISTERED.put(name, block);
        return block;
    }

    public static void initialize() {
        // Derive species from native planks so each native wood family uses the same remains.
        for (Block block : BuiltInRegistries.BLOCK) {
            var id = BuiltInRegistries.BLOCK.getKey(block);
            if (!id.getNamespace().equals("minecraft") || !id.getPath().endsWith("_planks")) continue;
            String wood = id.getPath().substring(0, id.getPath().length() - 7);
            REPLACEMENTS.put(block, PLANKS);
            add(wood + "_stairs", STAIRS); add(wood + "_slab", SLAB);
            add(wood + "_fence", FENCE); add(wood + "_fence_gate", GATE);
            add(wood + "_button", BUTTON); add(wood + "_pressure_plate", PRESSURE_PLATE);
        }
        add("bamboo_mosaic", PLANKS); add("bamboo_mosaic_stairs", STAIRS); add("bamboo_mosaic_slab", SLAB);
        // Buttons and plates are not in vanilla's fire table; they must be burnable too.
        for (Block source : REPLACEMENTS.keySet()) {
            if (!naturality.config.startup.FeatureConfig.Feature.FIRE_PROPAGATION.enabled()) break;
            var entry = FlammableBlockRegistry.getDefaultInstance().get(source);
            if (entry == null || entry.getBurnOdds() == 0)
                FlammableBlockRegistry.getDefaultInstance().add(source, 5, 20);
        }
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.BUILDING_BLOCKS)
            .register(output -> REGISTERED.values().forEach(output::accept));
    }

    private static void add(String path, Block target) {
        Block source = BuiltInRegistries.BLOCK.getValue(net.minecraft.resources.Identifier.withDefaultNamespace(path));
        if (source != null && source != Blocks.AIR) REPLACEMENTS.put(source, target);
    }

    public static @Nullable BlockState replacement(BlockState state) {
        Block block = state.getBlock();
        if (REGISTERED.containsValue(block)) return null;
        Block target = state.is(Blocks.GRASS_BLOCK) ? GRASS : state.is(BlockTags.LOGS) ? LOG : REPLACEMENTS.get(block);
        return target == null ? null : target.withPropertiesOf(state);
    }

    /** Keep grass mechanics, including snow and bonemeal, but never spread or revert. */
    private static final class ScorchedGrass extends GrassBlock {
        ScorchedGrass(BlockBehaviour.Properties properties) { super(properties); }
        @Override public void randomTick(BlockState state, net.minecraft.server.level.ServerLevel level,
                net.minecraft.core.BlockPos pos, net.minecraft.util.RandomSource random) { }
    }
}
