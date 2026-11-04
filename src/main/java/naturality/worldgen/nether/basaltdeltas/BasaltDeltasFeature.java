package naturality.worldgen.nether.basaltdeltas;

import com.mojang.serialization.MapCodec;
import java.util.ArrayList;
import naturality.Naturality;
import naturality.bootstrap.ContentRegistration.NaturalityBlocks;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.biome.v1.ModificationPhase;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.placement.NetherPlacements;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/** Decorations for all exposed floors, including the Nether's stacked cavern shelves. */
@org.jspecify.annotations.NullMarked
public record BasaltDeltasFeature() implements Feature {
    public static final MapCodec<BasaltDeltasFeature> CODEC = MapCodec.unit(BasaltDeltasFeature::new);
    private static final ResourceKey<PlacedFeature> PLACED = ResourceKey.create(
        Registries.PLACED_FEATURE, Naturality.id("basalt_deltas_decoration"));
    private static final ResourceKey<PlacedFeature> COLUMNS = ResourceKey.create(
        Registries.PLACED_FEATURE, Naturality.id("basalt_deltas_edge_columns"));

    public static void initialize() {
        Registry.register(BuiltInRegistries.FEATURE_TYPE, Naturality.id("basalt_deltas_decoration"), CODEC);
        Registry.register(BuiltInRegistries.FEATURE_TYPE, Naturality.id("basalt_deltas_edge_columns"), EdgeColumns.CODEC);
        BiomeModifications.create(Naturality.id("basalt_deltas_edge_columns")).add(ModificationPhase.REMOVALS,
            BiomeSelectors.includeByKey(Biomes.BASALT_DELTAS, IndustrialDeltas.BIOME), context -> {
                context.getGenerationSettings().removeFeature(NetherPlacements.SMALL_BASALT_COLUMNS);
                context.getGenerationSettings().removeFeature(NetherPlacements.LARGE_BASALT_COLUMNS);
            });
        BiomeModifications.addFeature(BiomeSelectors.includeByKey(Biomes.BASALT_DELTAS, IndustrialDeltas.BIOME),
            GenerationStep.Decoration.SURFACE_STRUCTURES, COLUMNS);
        BiomeModifications.addFeature(BiomeSelectors.includeByKey(Biomes.BASALT_DELTAS, IndustrialDeltas.BIOME),
            GenerationStep.Decoration.TOP_LAYER_MODIFICATION, PLACED);
    }

    @Override public MapCodec<BasaltDeltasFeature> codec() { return CODEC; }

    /** Adjacent chunks may already have ash coatings when vanilla places another delta pool. */
    public static boolean canContainLava(LevelAccessor level, BlockPos pos) {
        if (!IndustrialDeltas.isDelta(level.getBiome(pos))) return true;
        for (Direction direction : Direction.values()) {
            if (direction == Direction.UP) continue;
            BlockPos neighbor = pos.relative(direction);
            BlockState state = level.getBlockState(neighbor);
            var fluid = state.getFluidState();
            // Sources connect cells within a pool; flowing lava is not a containing wall.
            if (fluid.is(FluidTags.LAVA) && fluid.isSource()) continue;
            if (!fluid.isEmpty() || !state.isFaceSturdy(level, neighbor, direction.getOpposite())) return false;
        }
        return true;
    }

    /** Replaces vanilla's spreading column clusters with columns rooted on existing rims. */
    public record EdgeColumns() implements Feature {
        public static final MapCodec<EdgeColumns> CODEC = MapCodec.unit(EdgeColumns::new);

        @Override public MapCodec<EdgeColumns> codec() { return CODEC; }

        @Override public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
            if (level.getLevel().dimension() != Level.NETHER) return false;
            int startX = Math.floorDiv(origin.getX(), 16) * 16;
            int startZ = Math.floorDiv(origin.getZ(), 16) * 16;
            SimplexNoise heights = new SimplexNoise(RandomSource.create(level.getSeed() ^ 0x5b31e9a74L));
            var columns = new ArrayList<Column>();
            var ground = new BlockPos.MutableBlockPos();
            var target = new BlockPos.MutableBlockPos();
            var neighbor = new BlockPos.MutableBlockPos();
            // Plan all cavern floors before writing anything. Generated columns must
            // never become new ledges that let the next column spread into the interior.
            // Writes belong only to this chunk, so adjacent chunks cannot seed columns here.
            for (int z = startZ; z < startZ + 16; z++) for (int x = startX; x < startX + 16; x++) {
                for (int y = level.getMaxY() - 1; y > level.getMinY(); y--) {
                    ground.set(x, y, z);
                    target.set(x, y + 1, z);
                    if (!level.ensureCanWrite(target) || !level.getBlockState(target).isAir()) continue;
                    BlockState support = level.getBlockState(ground);
                    if (!(support.is(Blocks.BASALT) || support.is(Blocks.BLACKSTONE) || support.is(Blocks.NETHERRACK)
                            || support.is(Blocks.MAGMA_BLOCK) || support.is(NaturalityBlocks.ASH_BLOCK))
                            || !IndustrialDeltas.isDelta(level.getBiome(ground))
                            || !isTerrainEdge(level, ground, neighbor)) continue;
                    if (level.getBiome(ground).is(IndustrialDeltas.BIOME) && !IndustrialDeltas.pillarSite(x, z)) continue;
                    double shape = heights.get(x / 12.0, y / 32.0, z / 12.0);
                    if (shape < -.55) continue;
                    int height = 1 + (int) Math.floor(Math.clamp((shape + 1) * .5, 0, 1) * 7) + random.nextInt(3);
                    columns.add(new Column(x, y + 1, z, height));
                }
            }
            boolean placed = false;
            for (Column column : columns) {
                target.set(column.x, column.y, column.z);
                for (int h = 0; h < column.height; h++) {
                    if (target.getY() > level.getMaxY() || !level.ensureCanWrite(target)
                            || !level.getBlockState(target).isAir()
                            || !IndustrialDeltas.isDelta(level.getBiome(target))) break;
                    if (!level.setBlock(target, Blocks.BASALT.defaultBlockState(), 2)) break;
                    placed = true;
                    target.move(Direction.UP);
                }
            }
            return placed;
        }
    }

    private record Column(int x, int y, int z, int height) {}

    @Override public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
        if (level.getLevel().dimension() != Level.NETHER) return false;
        int startX = Math.floorDiv(origin.getX(), 16) * 16;
        int startZ = Math.floorDiv(origin.getZ(), 16) * 16;
        // World-seeded fields continue across chunks; decoration order and cap height
        // must not turn one ash patch into independent per-block placement rolls.
        RandomSource ashRandom = RandomSource.create(level.getSeed() ^ 0x6b49a32d17L);
        SimplexNoise ashPatches = new SimplexNoise(ashRandom);
        SimplexNoise ashEdges = new SimplexNoise(ashRandom);
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
        var neighbor = new BlockPos.MutableBlockPos();
        var buildings = new ArrayList<BoundingBox>();
        if (level instanceof WorldGenRegion region) {
            var structures = level.getLevel().structureManager().forWorldGenRegion(region);
            for (var start : structures.startsForStructure(startX >> 4, startZ >> 4, _ -> true))
                for (var piece : start.getPieces()) buildings.addAll(
                    naturality.worldgen.nether.fortress.FortressBridges.coveredInteriorBounds(piece));
        }
        for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
            var sheltered = shelteredHeights(level, startX + x, startZ + z, buildings);
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
                // Fitted ash follows partial surfaces even without a full top face.
                boolean fittedSupport = support.is(Blocks.NETHER_BRICK_WALL)
                    || support.is(Blocks.NETHER_BRICK_FENCE)
                    || support.getBlock() instanceof net.minecraft.world.level.block.SlabBlock
                    || support.getBlock() instanceof net.minecraft.world.level.block.StairBlock;
                if (support.is(Blocks.BEDROCK) || (!fittedSupport && !support.isFaceSturdy(level, ground, Direction.UP))
                        || !support.getFluidState().isEmpty()
                        || !IndustrialDeltas.isDelta(level.getBiome(ground))) continue;
                boolean ashPatch = ashPatches.get(ground.getX() / 18.0, y / 32.0, ground.getZ() / 18.0)
                    + .2 * ashEdges.get(ground.getX() / 6.0, y / 32.0, ground.getZ() / 6.0) > -.2;
                if (support.is(Blocks.BASALT) && random.nextFloat() < 0.08F && isTerrainEdge(level, ground, neighbor)) {
                    int height = 1 + random.nextInt(5);
                    for (int h = 0; h < height; h++) {
                        if (!level.ensureCanWrite(target) || !level.getBlockState(target).isAir()
                                || !IndustrialDeltas.isDelta(level.getBiome(target))) break;
                        if (!level.setBlock(target, NaturalityBlocks.BASALT_WALL.defaultBlockState(), 2)) break;
                        placed = true;
                        target.move(Direction.UP);
                    }
                } else if (moundHeight > 0 && !sheltered.get(target.getY() - level.getMinY())
                        && (support.is(Blocks.BASALT) || support.is(Blocks.BLACKSTONE)
                        || support.is(Blocks.NETHERRACK))) {
                    for (int h = 0; h < moundHeight; h++) {
                        if (!level.ensureCanWrite(target) || !level.getBlockState(target).isAir()
                                || !IndustrialDeltas.isDelta(level.getBiome(target))) break;
                        placed |= level.setBlock(target, NaturalityBlocks.ASH_BLOCK.defaultBlockState(), 2);
                        ground.set(target);
                        target.move(Direction.UP);
                    }
                }
                // The target is above the finished wall cap, ash mound or original floor.
                if (ashPatch && level.ensureCanWrite(target)
                        && !sheltered.get(target.getY() - level.getMinY())
                        && level.getBlockState(target).isAir()
                        && IndustrialDeltas.isDelta(level.getBiome(target))
                        && NaturalityBlocks.ASH.defaultBlockState().canSurvive(level, target)) {
                    placed |= level.setBlock(target, NaturalityBlocks.ASH.defaultBlockState(), 2);
                }
            }
        }
        return placed;
    }

    /** Only roofs inside structure pieces shelter interiors; the Nether ceiling is not a roof. */
    private static java.util.BitSet shelteredHeights(net.minecraft.world.level.BlockGetter level, int x, int z,
            java.util.List<BoundingBox> buildings) {
        var sheltered = new java.util.BitSet();
        var pos = new BlockPos.MutableBlockPos();
        for (var box : buildings) {
            if (x < box.minX() || x > box.maxX() || z < box.minZ() || z > box.maxZ()) continue;
            boolean roof = false;
            for (int y = Math.min(box.maxY(), level.getMaxY()); y >= Math.max(box.minY(), level.getMinY()); y--) {
                if (roof) sheltered.set(y - level.getMinY());
                pos.set(x, y, z);
                var state = level.getBlockState(pos);
                if (roof || state.isAir() || !state.getFluidState().isEmpty()) continue;
                // Full horizontal coverage also recognizes slab/stair roofs, without
                // treating an open fence or wall railing as a solid ceiling.
                for (var shape : state.getCollisionShape(level, pos).toAabbs()) {
                    if (shape.minX <= 0 && shape.maxX >= 1 && shape.minZ <= 0 && shape.maxZ >= 1
                            && shape.maxY > shape.minY) {
                        roof = true;
                        break;
                    }
                }
            }
        }
        return sheltered;
    }

    private static boolean isTerrainEdge(WorldGenLevel level, BlockPos ground, BlockPos.MutableBlockPos neighbor) {
        if (ground.getY() <= level.getMinY()) return false;
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            neighbor.setWithOffset(ground, direction);
            // Reads stay within the supplied generation region, including adjacent
            // chunks; a chunk boundary itself is never treated as a terrain edge.
            if (!level.ensureCanWrite(neighbor) || !isEdgeOpening(level.getBlockState(neighbor))) continue;
            neighbor.move(Direction.DOWN);
            if (isEdgeOpening(level.getBlockState(neighbor))) return true;
        }
        return false;
    }

    private static boolean isEdgeOpening(BlockState state) {
        // Earlier ash coatings and wall caps do not close an existing ledge.
        return state.isAir() || !state.getFluidState().isEmpty()
            || state.is(NaturalityBlocks.ASH) || state.is(NaturalityBlocks.BASALT_WALL);
    }

}
