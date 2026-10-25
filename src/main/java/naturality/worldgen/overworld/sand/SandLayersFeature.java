package naturality.worldgen.overworld.sand;

import com.mojang.serialization.MapCodec;
import naturality.Naturality;
import naturality.blocks.sand.SandLayerBlock;
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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

/** Smooth sandy surfaces by cutting high edges and filling low edges with supported layers. */
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
        // Read a three-block halo, but write only this chunk. In 26.3 the WG
        // heightmaps stop updating after TERRAIN; feature writes update the final
        // heightmaps instead. Keep those original elevations, including where a
        // neighboring chunk has already replaced its top sand with a partial layer.
        for (int z = 0; z < 22; z++) for (int x = 0; x < 22; x++) {
            int wx = startX + x - 3, wz = startZ + z - 3;
            int y = level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, wx, wz) - 1;
            heights[x][z] = y;
            var surface = level.getBlockState(pos.set(wx, y, wz));
            sand[x][z] = surface.is(Blocks.SAND) || surface.is(NaturalityBlocks.SAND_LAYER);
        }
        boolean placed = false;
        for (int z = 3; z < 19; z++) for (int x = 3; x < 19; x++) {
            if (!sand[x][z]) continue;
            int y = heights[x][z];
            int wx = startX + x - 3, wz = startZ + z - 3;
            double radius = 2.5 + 0.5 * variation(level.getSeed(), wx, wz);
            double totalWeight = 1.0;
            double heightChange = 0.0;
            for (int dz = -3; dz <= 3; dz++) for (int dx = -3; dx <= 3; dx++) {
                double distance = Math.sqrt(dx * dx + dz * dz);
                if (distance == 0 || distance >= radius) continue;
                // Do not pull sand toward exposed rock or other surface materials.
                if (!sand[x + dx][z + dz]) continue;
                double weight = 1.0 - distance / radius;
                weight *= weight;
                totalWeight += weight;
                int difference = heights[x + dx][z + dz] - y;
                // Use actual height differences, rather than just their sign, so
                // successive terraces blend into one surface. Bound cliff influence.
                heightChange += Math.clamp(difference, -2, 2) * weight;
            }
            int eighths = Math.clamp((int) Math.round(8.0 * heightChange / totalWeight), -7, 8);
            if (eighths == 0) continue;
            // At most the top sand block is cut or one cap is added. Keeping at
            // least one layer avoids exposing the substrate or opening water holes.
            pos.set(wx, y + 1, wz);
            var above = level.getBlockState(pos);
            boolean water = above.is(Blocks.WATER) && above.getFluidState().isSource();
            if (!above.isAir() && !water) continue;
            if (eighths < 0) pos.setY(y);
            if (!level.ensureCanWrite(pos)) continue;
            var previous = level.getBlockState(pos);
            if (eighths < 0 ? !previous.is(Blocks.SAND) : !previous.isAir() && !water) continue;
            int layers = eighths < 0 ? 8 + eighths : eighths;
            // Cutting a shoreline block can expose a source beside the new layer
            // even when the block above it is dry. Check at the final layer height.
            boolean waterlogged = water;
            if (!waterlogged) for (var direction : Direction.Plane.HORIZONTAL) {
                var neighbor = level.getBlockState(pos.relative(direction));
                // Only existing water blocks seed this, so newly waterlogged layers
                // cannot spread water inland depending on column/chunk write order.
                if (neighbor.is(Blocks.WATER) && neighbor.getFluidState().isSource()) {
                    waterlogged = true;
                    break;
                }
            }
            var state = NaturalityBlocks.SAND_LAYER.defaultBlockState()
                .setValue(SandLayerBlock.LAYERS, layers).setValue(SandLayerBlock.WATERLOGGED, waterlogged);
            if (state.canSurvive(level, pos)) {
                level.setBlock(pos, state, 2);
                placed = true;
            }
        }
        return placed;
    }

    /** Smooth world-seeded variation, independent of chunk order and feature RNG. */
    private static double variation(long seed, int x, int z) {
        int cellX = Math.floorDiv(x, 8), cellZ = Math.floorDiv(z, 8);
        double fx = Math.floorMod(x, 8) / 8.0, fz = Math.floorMod(z, 8) / 8.0;
        fx = fx * fx * (3.0 - 2.0 * fx);
        fz = fz * fz * (3.0 - 2.0 * fz);
        double north = sample(seed, cellX, cellZ) * (1.0 - fx) + sample(seed, cellX + 1, cellZ) * fx;
        double south = sample(seed, cellX, cellZ + 1) * (1.0 - fx) + sample(seed, cellX + 1, cellZ + 1) * fx;
        return north * (1.0 - fz) + south * fz;
    }

    private static double sample(long seed, int x, int z) {
        long hash = seed ^ (x * 0x632be59bd9b4e019L) ^ (z * 0x9e3779b97f4a7c15L);
        hash = (hash ^ (hash >>> 30)) * 0xbf58476d1ce4e5b9L;
        hash = (hash ^ (hash >>> 27)) * 0x94d049bb133111ebL;
        return ((hash ^ (hash >>> 31)) >>> 11) * 0x1.0p-53;
    }
}
