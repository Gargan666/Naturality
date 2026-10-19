package naturality.worldgen.nether.basaltdeltas;

import com.mojang.serialization.MapCodec;
import naturality.Naturality;
import naturality.bootstrap.ContentRegistration.NaturalityBlocks;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

/** Decorations for all exposed floors, including the Nether's stacked cavern shelves. */
@org.jspecify.annotations.NullMarked
public record BasaltDeltasFeature() implements Feature {
    public static final MapCodec<BasaltDeltasFeature> CODEC = MapCodec.unit(BasaltDeltasFeature::new);
    private static final ResourceKey<PlacedFeature> PLACED = ResourceKey.create(
        Registries.PLACED_FEATURE, Naturality.id("basalt_deltas_decoration"));

    public static void initialize() {
        Registry.register(BuiltInRegistries.FEATURE_TYPE, Naturality.id("basalt_deltas_decoration"), CODEC);
        BiomeModifications.addFeature(BiomeSelectors.includeByKey(Biomes.BASALT_DELTAS),
            GenerationStep.Decoration.TOP_LAYER_MODIFICATION, PLACED);
    }

    @Override public MapCodec<BasaltDeltasFeature> codec() { return CODEC; }

    @Override public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
        if (level.getLevel().dimension() != Level.NETHER) return false;
        int startX = Math.floorDiv(origin.getX(), 16) * 16;
        int startZ = Math.floorDiv(origin.getZ(), 16) * 16;
        int count = 2 + random.nextInt(3);
        int[] centersX = new int[count], centersZ = new int[count], radii = new int[count];
        for (int i = 0; i < count; i++) {
            radii[i] = 2 + random.nextInt(2);
            centersX[i] = radii[i] + random.nextInt(16 - radii[i] * 2);
            centersZ[i] = radii[i] + random.nextInt(16 - radii[i] * 2);
        }
        boolean placed = false;
        var ground = new BlockPos.MutableBlockPos();
        var target = new BlockPos.MutableBlockPos();
        for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
            int moundHeight = 0;
            for (int i = 0; i < count; i++) {
                int dx = x - centersX[i], dz = z - centersZ[i];
                int distance = dx * dx + dz * dz;
                if (distance <= radii[i] * radii[i]) moundHeight = Math.max(moundHeight, distance <= 1 ? 2 : 1);
            }
            // Never use a surface heightmap here: it would select the bedrock roof.
            for (int y = level.getMaxY() - 1; y >= level.getMinY(); y--) {
                ground.set(startX + x, y, startZ + z);
                target.set(startX + x, y + 1, startZ + z);
                if (!level.ensureCanWrite(target) || !level.getBlockState(target).isAir()) continue;
                var support = level.getBlockState(ground);
                if (support.is(Blocks.BEDROCK) || !support.isFaceSturdy(level, ground, Direction.UP)
                        || !support.getFluidState().isEmpty()
                        || !level.getBiome(ground).is(Biomes.BASALT_DELTAS)) continue;
                if (support.is(Blocks.BASALT) && random.nextFloat() < 0.4F) {
                    int height = 1 + random.nextInt(5);
                    for (int h = 0; h < height; h++) {
                        if (!level.ensureCanWrite(target) || !level.getBlockState(target).isAir()
                                || !level.getBiome(target).is(Biomes.BASALT_DELTAS)) break;
                        if (!level.setBlock(target, NaturalityBlocks.BASALT_WALL.defaultBlockState(), 2)) break;
                        placed = true;
                        target.move(Direction.UP);
                    }
                } else if (moundHeight > 0 && (support.is(Blocks.BASALT) || support.is(Blocks.BLACKSTONE)
                        || support.is(Blocks.NETHERRACK))) {
                    for (int h = 0; h < moundHeight; h++) {
                        if (!level.ensureCanWrite(target) || !level.getBlockState(target).isAir()
                                || !level.getBiome(target).is(Biomes.BASALT_DELTAS)) break;
                        placed |= level.setBlock(target, NaturalityBlocks.ASH_BLOCK.defaultBlockState(), 2);
                        ground.set(target);
                        target.move(Direction.UP);
                    }
                }
                // The target is above the finished wall cap, ash mound or original floor.
                if (random.nextFloat() < 0.7F && level.ensureCanWrite(target)
                        && level.getBlockState(target).isAir()
                        && level.getBiome(target).is(Biomes.BASALT_DELTAS)
                        && NaturalityBlocks.ASH.defaultBlockState().canSurvive(level, target)) {
                    placed |= level.setBlock(target, NaturalityBlocks.ASH.defaultBlockState(), 2);
                }
            }
        }
        return placed;
    }

}
