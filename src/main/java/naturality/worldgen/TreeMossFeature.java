package naturality.worldgen;

import com.mojang.serialization.MapCodec;
import java.util.ArrayList;
import naturality.Naturality;
import naturality.NaturalityBlocks;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.MultifaceBlock;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

/** Surface moss around living tree bases, with explicit biome eligibility. */
public record TreeMossFeature() implements Feature {
    public static final MapCodec<TreeMossFeature> CODEC = MapCodec.unit(TreeMossFeature::new);

    public static int rarity(ResourceKey<Biome> biome) {
        if (biome.equals(Biomes.OLD_GROWTH_SPRUCE_TAIGA) || biome.equals(Biomes.OLD_GROWTH_PINE_TAIGA)) return 1;
        if (biome.equals(Biomes.FOREST) || biome.equals(Biomes.FLOWER_FOREST)
                || biome.equals(Biomes.DARK_FOREST) || biome.equals(Biomes.TAIGA)
                || biome.equals(Biomes.WINDSWEPT_FOREST)) return 4;
        if (biome.equals(Biomes.BIRCH_FOREST) || biome.equals(Biomes.OLD_GROWTH_BIRCH_FOREST)) return 16;
        return 0;
    }

    public static void initialize() {
        Registry.register(BuiltInRegistries.FEATURE_TYPE, Naturality.id("tree_moss"), CODEC);
        for (int tier : new int[]{1, 4, 16}) {
            ResourceKey<PlacedFeature> placed = ResourceKey.create(Registries.PLACED_FEATURE, Naturality.id("tree_moss_" + tier));
            BiomeModifications.addFeature(BiomeSelectors.foundInOverworld().and(context -> rarity(context.getBiomeKey()) == tier),
                GenerationStep.Decoration.VEGETAL_DECORATION, placed);
        }
    }

    @Override public MapCodec<TreeMossFeature> codec() { return CODEC; }

    private static boolean allowed(WorldGenLevel level, BlockPos pos) {
        return level.getBiome(pos).unwrapKey().map(TreeMossFeature::rarity).orElse(0) > 0;
    }

    @Override public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
        if (level.getLevel().dimension() != Level.OVERWORLD || !allowed(level, origin)) return false;
        var trees = new ArrayList<BlockPos>();
        for (int x = -6; x <= 6; x++) for (int z = -6; z <= 6; z++) {
            int wx = origin.getX() + x, wz = origin.getZ() + z;
            BlockPos top = new BlockPos(wx, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, wx, wz) - 1, wz);
            if (!level.ensureCanWrite(top) || !level.getBlockState(top).is(BlockTags.LOGS)) continue;
            BlockPos base = top;
            for (int y = 0; y < 32 && level.getBlockState(base.below()).is(BlockTags.LOGS); y++) base = base.below();
            if (soil(level.getBlockState(base.below())) && allowed(level, base)) trees.add(base);
        }
        if (trees.isEmpty()) return false;
        BlockPos tree = trees.get(random.nextInt(trees.size()));
        int radius = 4 + random.nextInt(2);
        var groundPatch = new ArrayList<BlockPos>();
        for (Direction side : Direction.Plane.HORIZONTAL) {
            BlockPos seed = ground(level, tree.relative(side).getX(), tree.relative(side).getZ());
            if (seed != null && Math.abs(seed.getY() - tree.getY()) <= 1) groundPatch.add(seed);
            if (!groundPatch.isEmpty()) break;
        }
        if (groundPatch.isEmpty()) return false;
        // Grow one connected footprint. Random frontier choices make an uneven edge,
        // while every added column touches a column already in the patch.
        int targetSize = 24 + random.nextInt(25);
        for (int attempt = 0; attempt < targetSize * 40 && groundPatch.size() < targetSize; attempt++) {
            BlockPos from = groundPatch.get(random.nextInt(groundPatch.size()));
            Direction side = Direction.from2DDataValue(random.nextInt(4));
            int x = from.getX() + side.getStepX(), z = from.getZ() + side.getStepZ();
            if (Math.hypot(x - tree.getX(), z - tree.getZ()) > radius) continue;
            BlockPos next = ground(level, x, z);
            if (next != null && Math.abs(next.getY() - from.getY()) <= 1
                    && Math.abs(next.getY() - tree.getY()) <= 4 && !groundPatch.contains(next)) groundPatch.add(next);
        }
        // Skip cramped locations rather than generating isolated flecks.
        if (groundPatch.size() < 8) return false;
        boolean placed = false;
        for (BlockPos pos : groundPatch) {
            placed |= attach(level, pos, Direction.DOWN);
            for (Direction side : Direction.Plane.HORIZONTAL) {
                if (soil(level.getBlockState(pos.relative(side)))) placed |= attach(level, pos, side);
            }
        }
        // Coat continuous strips from a ground-connected base; never random single
        // trunk blocks suspended above missing moss.
        for (Direction side : Direction.Plane.HORIZONTAL) {
            BlockPos base = tree.relative(side);
            if (!groundPatch.contains(base)) continue;
            int height = 2 + random.nextInt(3);
            for (int y = 0; y < height; y++) {
                BlockPos trunk = tree.above(y);
                if (!level.getBlockState(trunk).is(BlockTags.LOGS)
                        || !attach(level, trunk.relative(side), side.getOpposite())) break;
                placed = true;
            }
        }
        return placed;
    }

    private static BlockPos ground(WorldGenLevel level, int x, int z) {
        BlockPos pos = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
        if (!level.ensureCanWrite(pos) || !allowed(level, pos) || !soil(level.getBlockState(pos.below()))) return null;
        var state = level.getBlockState(pos);
        return state.isAir() || state.is(NaturalityBlocks.MOSS) ? pos : null;
    }

    private static boolean soil(net.minecraft.world.level.block.state.BlockState state) {
        return state.is(BlockTags.DIRT) || state.is(net.minecraft.world.level.block.Blocks.GRASS_BLOCK)
            || state.is(net.minecraft.world.level.block.Blocks.PODZOL)
            || state.is(net.minecraft.world.level.block.Blocks.MYCELIUM)
            || state.is(net.minecraft.world.level.block.Blocks.MOSS_BLOCK);
    }

    private static boolean attach(WorldGenLevel level, BlockPos pos, Direction face) {
        if (!level.ensureCanWrite(pos) || !allowed(level, pos)) return false;
        var existing = level.getBlockState(pos);
        if (!existing.isAir() && !existing.is(NaturalityBlocks.MOSS)) return false;
        var support = pos.relative(face);
        if (!level.getBlockState(support).isFaceSturdy(level, support, face.getOpposite())) return false;
        var state = (existing.is(NaturalityBlocks.MOSS) ? existing : NaturalityBlocks.MOSS.defaultBlockState())
            .setValue(MultifaceBlock.getFaceProperty(face), true);
        return state == existing || level.setBlock(pos, state, 2);
    }
}
