package naturality.worldgen.nether.basaltdeltas;

import com.mojang.serialization.MapCodec;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import naturality.Naturality;
import naturality.blocks.lattice.IronLatticeBlock;
import naturality.bootstrap.ContentRegistration.NaturalityBlocks;
import naturality.worldgen.shared.StructureProtection;
import net.fabricmc.fabric.api.biome.v1.NetherBiomes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.FluidTags;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jspecify.annotations.NullMarked;

/** Delta variant using the existing lattice and basalt blocks, with fully planned crossings. */
@NullMarked
public final class IndustrialDeltas {
    public static final ResourceKey<Biome> BIOME = ResourceKey.create(Registries.BIOME, Naturality.id("industrial_deltas"));
    private static final int HALF_WIDTH = 2;
    private static final int MAX_REACH = 20;

    private IndustrialDeltas() {}

    public static void initialize() {
        Registry.register(BuiltInRegistries.FEATURE_TYPE, Naturality.id("industrial_lattice_fences"), Fences.CODEC);
        Registry.register(BuiltInRegistries.FEATURE_TYPE, Naturality.id("industrial_lattice_bridges"), Bridges.CODEC);
        for (String id : List.of("industrial_lattice_fences", "industrial_lattice_bridges")) {
            ResourceKey<PlacedFeature> feature = ResourceKey.create(Registries.PLACED_FEATURE, Naturality.id(id));
            BiomeModifications.addFeature(BiomeSelectors.includeByKey(BIOME), GenerationStep.Decoration.VEGETAL_DECORATION, feature);
        }
        // Colder part of the basalt climate, leaving ordinary deltas around its margin.
        NetherBiomes.addNetherBiome(BIOME, Climate.parameters(-.8F, 0, 0, 0, 0, 0, .175F));
    }

    public static boolean isDelta(Holder<Biome> biome) {
        return biome.is(Biomes.BASALT_DELTAS) || biome.is(BIOME);
    }

    public static boolean pillarSite(int x, int z) {
        // Shared world grid keeps columns four blocks apart even across chunk borders.
        return Math.floorMod(x, 4) == 0 && Math.floorMod(z, 4) == 0;
    }

    public record Fences() implements Feature {
        public static final MapCodec<Fences> CODEC = MapCodec.unit(Fences::new);
        @Override public MapCodec<Fences> codec() { return CODEC; }
        @Override public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
            return fences(level, Math.floorDiv(origin.getX(), 16) * 16, Math.floorDiv(origin.getZ(), 16) * 16);
        }
    }

    public record Bridges() implements Feature {
        public static final MapCodec<Bridges> CODEC = MapCodec.unit(Bridges::new);
        @Override public MapCodec<Bridges> codec() { return CODEC; }
        @Override public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
            // /place uses the requested position as the deck center and never rolls rarity.
            if (!(level instanceof WorldGenRegion)) {
                for (int dy : new int[] {0, 1, -1, 2, -2, 3, -3}) {
                    for (int dx : new int[] {0, -4, 4}) for (int dz : new int[] {0, -4, 4}) {
                        if (crossing(level, origin.offset(dx, dy, dz), random)) return true;
                    }
                }
                return false;
            }
            int x = Math.floorDiv(origin.getX(), 16) * 16 + 8;
            int z = Math.floorDiv(origin.getZ(), 16) * 16 + 8;
            // Search every chunk and spread candidates across its footprint, rather
            // than relying on one centre column in one quarter of the chunks.
            // Keep one successful crossing per chunk to bound decoration density.
            int firstCandidate = random.nextInt(9);
            for (int y = level.getMaxY() - 3; y > level.getMinY() + 4; y--) {
                for (int attempt = 0; attempt < 6; attempt++) {
                    int candidate = (firstCandidate + attempt) % 9;
                    BlockPos bend = new BlockPos(x + (candidate % 3 - 1) * 4, y,
                        z + (candidate / 3 - 1) * 4);
                    if (!clear(level, bend) || !allowed(level, bend)) continue;
                    var below = level.getBlockState(bend.below());
                    if (!(below.isAir() || !below.getFluidState().isEmpty())) continue;
                    if (crossing(level, bend, random)) return true;
                }
            }
            return false;
        }
    }

    private static boolean crossing(WorldGenLevel level, BlockPos bend, RandomSource random) {
        // All six distinct layouts share the same four wall searches. Failed
        // candidates used to repeat these scans for every pair of directions.
        int[] lengths = new int[4];
        for (int i = 0; i < 4; i++) lengths[i] = wall(level, bend, Direction.from2DDataValue(i));
        Direction first = Direction.from2DDataValue(random.nextInt(4));
        for (int i = 0; i < 4; i++, first = first.getClockWise()) {
            int a = lengths[first.get2DDataValue()];
            if (a < 3) continue;
            Direction opposite = first.getOpposite();
            Direction turn = first.getClockWise();
            if ((i < 2 && bridge(level, bend, first, opposite, a, lengths[opposite.get2DDataValue()]))
                    || bridge(level, bend, first, turn, a, lengths[turn.get2DDataValue()])) return true;
        }
        return false;
    }

    private static boolean allowed(WorldGenLevel level, BlockPos pos) {
        if (!(level instanceof WorldGenRegion region)) return true;
        if (!region.isWithinWriteZone(pos)) return false;
        // Smoothed getBiome() samples neighbouring quart cells, potentially outside
        // this generation step's dependency cache even when pos itself is writable.
        // Read the containing chunk's already-generated biome palette instead.
        return region.getChunk(pos.getX() >> 4, pos.getZ() >> 4)
            .getNoiseBiome(pos.getX() >> 2, pos.getY() >> 2, pos.getZ() >> 2).is(BIOME);
    }

    private static boolean fences(WorldGenLevel level, int startX, int startZ) {
        boolean placed = false;
        for (int x = startX; x < startX + 16; x++) for (int z = startZ; z < startZ + 16; z++) {
            for (int y = level.getMinY() + 2; y < level.getMaxY() - 2; y++) {
                BlockPos anchor = new BlockPos(x, y, z);
                if (!allowed(level, anchor) || !level.getBlockState(anchor).is(Blocks.BASALT)) continue;
                for (Direction along : List.of(Direction.EAST, Direction.SOUTH)) {
                    List<BlockPos> gap = new ArrayList<>();
                    for (int length = 1; length <= 8; length++) {
                        BlockPos at = anchor.relative(along, length);
                        if (!writable(level, at) || !allowed(level, at)) break;
                        BlockState state = level.getBlockState(at);
                        if (state.is(Blocks.BASALT)) {
                            if (gap.size() >= 2) {
                                BlockState lattice = NaturalityBlocks.IRON_LATTICE.defaultBlockState()
                                    .setValue(IronLatticeBlock.FACING, along.getClockWise());
                                Map<BlockPos, BlockState> plan = new LinkedHashMap<>();
                                boolean grounded = true;
                                for (BlockPos p : gap) {
                                    var floor = level.getBlockState(p.below());
                                    if (!floor.getFluidState().isEmpty() || !floor.isFaceSturdy(level, p.below(), Direction.UP)) grounded = false;
                                }
                                if (!grounded) break;
                                int height = 0;
                                for (int h = 0; y + h < level.getMaxY(); h++) {
                                    if (!level.getBlockState(anchor.above(h)).is(Blocks.BASALT)
                                            || !level.getBlockState(at.above(h)).is(Blocks.BASALT)) break;
                                    boolean rowClear = true;
                                    for (BlockPos p : gap) if (!writable(level, p.above(h))
                                            || !allowed(level, p.above(h)) || !level.getBlockState(p.above(h)).isAir()) rowClear = false;
                                    if (!rowClear) break;
                                    for (BlockPos p : gap) plan.put(p.above(h), lattice);
                                    height++;
                                }
                                if (height < 3) plan.clear();
                                placed |= commit(level, plan);
                            }
                            break;
                        }
                        if (!state.isAir()) break;
                        gap.add(at);
                    }
                }
            }
        }
        return placed;
    }

    private static boolean bridge(WorldGenLevel level, BlockPos bend, Direction first, Direction second, int a, int b) {
        if (a < 3 || b < 3) return false;
        Map<BlockPos, Direction.Axis> deck = new LinkedHashMap<>();
        for (Direction direction : List.of(first, second)) {
            int length = direction == first ? a : b;
            for (int d = -HALF_WIDTH; d <= length; d++) {
                BlockPos center = bend.relative(direction, d);
                for (int side = -HALF_WIDTH; side <= HALF_WIDTH; side++) {
                    deck.put(center.relative(direction.getClockWise(), side), direction.getAxis());
                }
            }
        }
        Map<BlockPos, BlockState> plan = new LinkedHashMap<>();
        for (var entry : deck.entrySet()) {
            BlockPos pos = entry.getKey();
            if (!writable(level, pos) || !allowed(level, pos)) return false;
            // Preserve irregular rock faces: the deck terminates against protruding
            // wall blocks instead of requiring the whole endpoint to be a flat plane.
            if (naturalFloor(level.getBlockState(pos)) && level.getBlockState(pos).isCollisionShapeFullBlock(level, pos)) continue;
            if (!clear(level, pos)) return false;
            boolean edge = false;
            for (Direction dir : Direction.Plane.HORIZONTAL) if (!deck.containsKey(pos.relative(dir))) edge = true;
            // Overlapping arms overwrite the stored axis at elbows. Derive straight
            // perimeter beams from their exposed side instead of the last arm written.
            boolean exposedX = !deck.containsKey(pos.west()) || !deck.containsKey(pos.east());
            boolean exposedZ = !deck.containsKey(pos.north()) || !deck.containsKey(pos.south());
            Direction.Axis beamAxis = exposedX == exposedZ ? entry.getValue()
                : exposedX ? Direction.Axis.Z : Direction.Axis.X;
            boolean attachment = pos.distManhattan(bend.relative(first, a)) <= 3
                || pos.distManhattan(bend.relative(second, b)) <= 3;
            // Only open the middle of an attachment, never its longitudinal rails.
            if (attachment && (openEnd(pos, bend, first, a) || openEnd(pos, bend, second, b))) edge = false;
            // Endpoints attach to walls, including overhangs; they need no exit ledge.
            if (!edge && !attachment && (!clear(level, pos.above()) || !clear(level, pos.above(2)))) return false;
            plan.put(pos, edge ? Blocks.POLISHED_BASALT.defaultBlockState().setValue(RotatedPillarBlock.AXIS, beamAxis)
                : NaturalityBlocks.IRON_LATTICE.defaultBlockState().setValue(IronLatticeBlock.FACING, Direction.UP));
        }
        // Each rail meets its own wall face, which can recede beyond the centre
        // attachment. Keep these extensions as beams, without widening the deck.
        for (Direction direction : List.of(first, second)) {
            Direction across = direction.getClockWise();
            for (int side : new int[] {-HALF_WIDTH, HALF_WIDTH}) {
                BlockPos rail = bend.relative(across, side);
                int length = wall(level, rail, direction);
                if (length < 0) return false;
                for (int d = HALF_WIDTH + 1; d <= length; d++) {
                    plan.put(rail.relative(direction, d), Blocks.POLISHED_BASALT.defaultBlockState()
                        .setValue(RotatedPillarBlock.AXIS, direction.getAxis()));
                }
            }
        }
        if (first != second.getOpposite()) {
            for (int dx : new int[] {-2, 2}) for (int dz : new int[] {-2, 2}) {
                BlockPos foot = bend.offset(dx, 0, dz);
                if (support(level, foot, plan) || suspension(level, foot, plan)) {
                    plan.put(foot, Blocks.POLISHED_BASALT.defaultBlockState());
                }
            }
        }
        return commit(level, plan);
    }

    private static boolean openEnd(BlockPos pos, BlockPos bend, Direction direction, int length) {
        int dx = pos.getX() - bend.getX();
        int dz = pos.getZ() - bend.getZ();
        int along = dx * direction.getStepX() + dz * direction.getStepZ();
        int across = dx * direction.getStepZ() - dz * direction.getStepX();
        return along >= length - 3 && Math.abs(across) < HALF_WIDTH;
    }

    private static int wall(WorldGenLevel level, BlockPos bend, Direction direction) {
        for (int d = 1; d <= MAX_REACH; d++) {
            BlockPos at = bend.relative(direction, d);
            if (!writable(level, at) || !allowed(level, at)) return -1;
            BlockState state = level.getBlockState(at);
            if (naturalFloor(state) && state.isFaceSturdy(level, at, direction.getOpposite())) return d - 1;
            if (!clear(level, at)) return -1;
        }
        return -1;
    }

    private static boolean naturalFloor(BlockState state) {
        return !StructureProtection.isNetherBrick(state) && state.getFluidState().isEmpty()
            && !state.isAir() && !state.is(NaturalityBlocks.IRON_LATTICE);
    }

    private static boolean support(WorldGenLevel level, BlockPos top, Map<BlockPos, BlockState> plan) {
        boolean throughLava = false;
        for (int y = top.getY() - 1; y >= level.getMinY(); y--) {
            BlockPos at = new BlockPos(top.getX(), y, top.getZ());
            if (!writable(level, at)) return false;
            BlockState state = level.getBlockState(at);
            if (naturalFloor(state) && state.isFaceSturdy(level, at, Direction.UP)) {
                int length = top.getY() - y - 1;
                if (!throughLava && length > 20) return false;
                // Plan only after finding the footing, so an omitted support never
                // leaves a partial beam hanging above the floor.
                for (int beamY = y + 1; beamY < top.getY(); beamY++) {
                    plan.put(new BlockPos(top.getX(), beamY, top.getZ()), Blocks.POLISHED_BASALT.defaultBlockState());
                }
                return true;
            }
            if (!state.isAir() && state.getFluidState().isEmpty()) return false;
            throughLava |= state.getFluidState().is(FluidTags.LAVA);
        }
        return false;
    }

    private static boolean suspension(WorldGenLevel level, BlockPos foot, Map<BlockPos, BlockState> plan) {
        // The roof itself is not part of the chain's twenty-block length.
        for (int distance = 1; distance <= 21; distance++) {
            BlockPos at = foot.above(distance);
            if (!writable(level, at)) return false;
            BlockState state = level.getBlockState(at);
            if (naturalFloor(state) && state.isFaceSturdy(level, at, Direction.DOWN)) {
                for (int chain = 1; chain < distance; chain++) {
                    plan.put(foot.above(chain), Blocks.IRON_CHAIN.defaultBlockState());
                }
                return true;
            }
            if (!state.isAir()) return false;
        }
        return false;
    }

    private static boolean clear(WorldGenLevel level, BlockPos pos) {
        return writable(level, pos) && (level.getBlockState(pos).isAir() || level.getBlockState(pos).is(NaturalityBlocks.ASH));
    }

    private static boolean writable(WorldGenLevel level, BlockPos pos) {
        return pos.getY() >= level.getMinY() && pos.getY() <= level.getMaxY()
            && (level instanceof WorldGenRegion region ? region.isWithinWriteZone(pos) : level.ensureCanWrite(pos));
    }

    private static boolean commit(WorldGenLevel level, Map<BlockPos, BlockState> plan) {
        if (plan.isEmpty()) return false;
        BoundingBox bounds = BoundingBox.encapsulatingPositions(plan.keySet()).orElseThrow();
        if (StructureProtection.overlapsStructure(level, bounds)) return false;
        for (var entry : plan.entrySet()) {
            if (!writable(level, entry.getKey())) return false;
            if (level instanceof WorldGenRegion region && StructureProtection.blocksWrite(region, entry.getKey(), entry.getValue())) return false;
        }
        for (var entry : plan.entrySet()) level.setBlock(entry.getKey(), entry.getValue(), 2);
        return true;
    }
}
