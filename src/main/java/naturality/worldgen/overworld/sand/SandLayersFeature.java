package naturality.worldgen.overworld.sand;

import com.mojang.serialization.MapCodec;
import naturality.Naturality;
import naturality.blocks.sand.SandLayerBlock;
import naturality.bootstrap.ContentRegistration.NaturalityBlocks;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

/** Supported sand aprons on slopes and thin caps along sandy cliff rims. */
@org.jspecify.annotations.NullMarked
public record SandLayersFeature() implements Feature {
    public static final MapCodec<SandLayersFeature> CODEC = MapCodec.unit(SandLayersFeature::new);
    public static final ResourceKey<PlacedFeature> PLACED = ResourceKey.create(
        Registries.PLACED_FEATURE, Naturality.id("sand_layers"));

    public static void initialize() {
        Registry.register(BuiltInRegistries.FEATURE_TYPE, Naturality.id("sand_layers"), CODEC);
        BiomeModifications.addFeature(BiomeSelectors.foundInOverworld(), GenerationStep.Decoration.LOCAL_MODIFICATIONS, PLACED);
    }

    @Override public MapCodec<SandLayersFeature> codec() { return CODEC; }

    @Override public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
        if (level.getLevel().dimension() != Level.OVERWORLD) return false;
        int startX = Math.floorDiv(origin.getX(), 16) * 16;
        int startZ = Math.floorDiv(origin.getZ(), 16) * 16;
        int[][] heights = new int[22][22];
        boolean[][] sand = new boolean[22][22];
        var pos = new BlockPos.MutableBlockPos();
        // Read a three-block halo, but write only this chunk. Strip existing layer caps
        // from the snapshot so adjacent chunks do not grow each other's deposits.
        for (int z = 0; z < 22; z++) for (int x = 0; x < 22; x++) {
            int wx = startX + x - 3, wz = startZ + z - 3;
            int y = level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, wx, wz) - 1;
            if (level.getBlockState(pos.set(wx, y, wz)).is(NaturalityBlocks.SAND_LAYER)) y--;
            heights[x][z] = y;
            sand[x][z] = level.getBlockState(pos.set(wx, y, wz)).is(Blocks.SAND);
        }
        boolean placed = false;
        for (int z = 3; z < 19; z++) for (int x = 3; x < 19; x++) {
            if (!sand[x][z]) continue;
            int y = heights[x][z];
            int layers = 0;
            for (int dz = -3; dz <= 3; dz++) for (int dx = -3; dx <= 3; dx++) {
                int distance = Math.abs(dx) + Math.abs(dz);
                if (distance == 0 || distance > 3) continue;
                int difference = heights[x + dx][z + dz] - y;
                if (difference > 0 && sand[x + dx][z + dz]) {
                    layers = Math.max(layers, 8 - 2 * distance);
                } else if (difference <= -3) {
                    // A thin lip sits on the cliff's sand top, never unsupported over its side.
                    layers = Math.max(layers, 4 - distance);
                }
            }
            if (layers == 0) continue;
            pos.set(startX + x - 3, y + 1, startZ + z - 3);
            if (!level.ensureCanWrite(pos)) continue;
            var previous = level.getBlockState(pos);
            boolean water = previous.is(Blocks.WATER) && previous.getFluidState().isSource();
            if (!previous.isAir() && !water) continue;
            var state = NaturalityBlocks.SAND_LAYER.defaultBlockState()
                .setValue(SandLayerBlock.LAYERS, layers).setValue(SandLayerBlock.WATERLOGGED, water);
            if (state.canSurvive(level, pos)) {
                level.setBlock(pos, state, 2);
                placed = true;
            }
        }
        return placed;
    }
}
