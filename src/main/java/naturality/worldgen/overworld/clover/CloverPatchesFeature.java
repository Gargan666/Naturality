package naturality.worldgen.overworld.clover;

import com.mojang.serialization.MapCodec;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import naturality.Naturality;
import naturality.bootstrap.ContentRegistration.NaturalityBlocks;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalBiomeTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.FlowerBedBlock;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

/** Connected irregular clusters, with a strictly larger dominant patch. */
public record CloverPatchesFeature() implements Feature {
    public static final MapCodec<CloverPatchesFeature> CODEC = MapCodec.unit(CloverPatchesFeature::new);
    public static final ResourceKey<PlacedFeature> PLACED = ResourceKey.create(Registries.PLACED_FEATURE, Naturality.id("clover_patches"));

    public static void initialize() {
        Registry.register(BuiltInRegistries.FEATURE_TYPE, Naturality.id("clover_patches"), CODEC);
        BiomeModifications.addFeature(BiomeSelectors.foundInOverworld(), GenerationStep.Decoration.VEGETAL_DECORATION, PLACED);
    }

    @Override public MapCodec<CloverPatchesFeature> codec() { return CODEC; }

    @Override public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
        if (level.getLevel().dimension() != Level.OVERWORLD) return false;
        Set<BlockPos> reserved = new HashSet<>();
        List<List<BlockPos>> patches = new ArrayList<>();
        List<BlockPos> dominant = patch(level, origin, 12 + random.nextInt(9), reserved, random);
        if (dominant.size() < 8) return false;
        patches.add(dominant);
        reserve(dominant, reserved);
        int satellites = 1 + random.nextInt(3);
        double start = random.nextDouble() * Math.PI * 2;
        for (int i = 0; i < satellites; i++) {
            double angle = start + i * Math.PI * 2 / satellites + (random.nextDouble() - .5) * .5;
            int distance = 6 + random.nextInt(3);
            BlockPos center = origin.offset((int)Math.round(Math.cos(angle) * distance), 0, (int)Math.round(Math.sin(angle) * distance));
            List<BlockPos> small = patch(level, center, 3 + random.nextInt(4), reserved, random);
            if (small.size() >= 3) {
                patches.add(small);
                reserve(small, reserved);
            }
        }
        // Reject isolated patches; commit only after the full group has been validated.
        if (patches.size() < 2) return false;
        for (List<BlockPos> patch : patches) for (BlockPos pos : patch) {
            var clovers = random.nextFloat() < 0.01F ? NaturalityBlocks.FOUR_LEAF_CLOVERS : NaturalityBlocks.CLOVERS;
            level.setBlock(pos, clovers.defaultBlockState()
                .setValue(FlowerBedBlock.FACING, Direction.from2DDataValue(random.nextInt(4)))
                .setValue(FlowerBedBlock.AMOUNT, 2 + random.nextInt(3)), 2);
        }
        return true;
    }

    private static List<BlockPos> patch(WorldGenLevel level, BlockPos center, int count, Set<BlockPos> reserved, RandomSource random) {
        List<BlockPos> result = new ArrayList<>();
        BlockPos start = surface(level, center.getX(), center.getZ());
        if (start == null || blocked(start, reserved)) return result;
        result.add(start);
        for (int attempt = 0; attempt < count * 20 && result.size() < count; attempt++) {
            BlockPos from = result.get(random.nextInt(result.size()));
            Direction direction = Direction.from2DDataValue(random.nextInt(4));
            BlockPos neighbor = surface(level, from.getX() + direction.getStepX(), from.getZ() + direction.getStepZ());
            if (neighbor != null && Math.abs(neighbor.getY() - from.getY()) <= 1
                    && Math.abs(neighbor.getX() - center.getX()) <= 3 && Math.abs(neighbor.getZ() - center.getZ()) <= 3
                    && !result.contains(neighbor) && !blocked(neighbor, reserved)) result.add(neighbor);
        }
        return result;
    }

    private static BlockPos surface(WorldGenLevel level, int x, int z) {
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        BlockPos pos = new BlockPos(x, y, z);
        if (!level.ensureCanWrite(pos)) return null;
        var biome = level.getBiome(pos);
        // Check every candidate, including satellites and growth across biome borders.
        if (biome.is(ConventionalBiomeTags.IS_HOT) || biome.is(ConventionalBiomeTags.IS_MUSHROOM)) return null;
        return level.isEmptyBlock(pos)
            && NaturalityBlocks.CLOVERS.defaultBlockState().canSurvive(level, pos) ? pos : null;
    }

    private static boolean blocked(BlockPos pos, Set<BlockPos> reserved) {
        // Reserve horizontal columns so terrain steps cannot join separate patches.
        return reserved.contains(new BlockPos(pos.getX(), 0, pos.getZ()));
    }

    private static void reserve(List<BlockPos> patch, Set<BlockPos> reserved) {
        for (BlockPos pos : patch) for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++)
            reserved.add(new BlockPos(pos.getX() + x, 0, pos.getZ() + z));
    }
}
