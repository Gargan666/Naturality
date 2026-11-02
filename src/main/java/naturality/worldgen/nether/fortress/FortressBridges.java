package naturality.worldgen.nether.fortress;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.world.level.NoiseColumn;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.Codec;
import naturality.Naturality;
import naturality.blocks.lavalogging.BlockLavalogging;
import naturality.bootstrap.ContentRegistration;
import naturality.worldgen.nether.plains.NetherPlains;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.properties.WallSide;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.densityfunction.DensitySampler;
import net.minecraft.world.level.levelgen.densityfunction.DensityVolume;
import net.minecraft.world.level.levelgen.densityfunction.ScopedDensityBuffer;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.TemplateStructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/** Fortress layout and placement using the author's FORTRESS REBUILD exports. */
@NullMarked
public final class FortressBridges {
    private static final ThreadLocal<FoundationDensity> FOUNDATION_DENSITY = new ThreadLocal<>();
    private static final int BRIDGE_WIDTH = 4;
    private static final int DECK_Y = 4;
    private static final int SECTION_COUNT = 40;
    private static final int MAX_RADIUS = 96;
    private static final float BRIDGE_GATE_CHANCE = 0.25F;
    private static final StructurePieceType PIECE_TYPE = Registry.register(
        BuiltInRegistries.STRUCTURE_PIECE, Naturality.id("fortress_bridge"),
        (StructurePieceType.StructureTemplateType) BridgePiece::new);
    private static final StructurePieceType BUILDING_TYPE = Registry.register(
        BuiltInRegistries.STRUCTURE_PIECE, Naturality.id("fortress_building"),
        (StructurePieceType.StructureTemplateType) BuildingPiece::new);
    private static final StructurePieceType HALL_TYPE = Registry.register(
        BuiltInRegistries.STRUCTURE_PIECE, Naturality.id("fortress_hall"),
        (StructurePieceType.StructureTemplateType) HallPiece::new);

    private FortressBridges() {}

    /** Generation-only exclusion; player planting and mushroom survival remain vanilla. */
    public static boolean blocksMushroomGeneration(BlockState mushroom, LevelReader level, BlockPos pos) {
        if (!(mushroom.is(Blocks.RED_MUSHROOM) || mushroom.is(Blocks.BROWN_MUSHROOM)
                || mushroom.is(Blocks.CRIMSON_FUNGUS) || mushroom.is(Blocks.WARPED_FUNGUS))
                || !(level instanceof WorldGenLevel world)
                || !world.getLevel().dimension().equals(net.minecraft.world.level.Level.NETHER)) return false;
        Identifier support = BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos.below()).getBlock());
        // Includes vanilla and authored crimson/warped brick blocks, slabs, stairs and railings.
        return (support.getNamespace().equals("minecraft") || support.getNamespace().equals("naturality"))
            && support.getPath().contains("nether_brick");
    }

    public static void initialize() {
        // Register the saved piece loader before the built-in registry freezes.
    }

    /** Manually assigned from the author's outside-stone notes in FORTRESS REBUILD.
     * Bits refer to the untransformed template: north=1, east=2, south=4, west=8.
     * Structure-block saves do not interpret or capture these workshop notes.
     */
    private static Map<String, Integer> interiorWallMasks() {
        return Map.of(
            "single_06", 1,
            "single_09", 2 | 8,
            "single_10", 2,
            "double_03", 1,
            "double_04", 1 | 4,
            "square_02", 1 | 8,
            "t_04", 1);
    }

    /** Enclosed hall space, excluding the authored facade ledges and roof overhangs. */
    public static List<BoundingBox> coveredInteriorBounds(StructurePiece piece) {
        if (piece instanceof HallPiece hall) {
            BlockPos origin = hall.origin();
            List<BoundingBox> covered = new ArrayList<>();
            for (int z = 0; z < hall.footprint.depth; z++) {
                for (int x = 0; x < hall.footprint.width; x++) {
                    if (!hall.footprint.interior(x, z)) continue;
                    int start = x;
                    while (hall.footprint.interior(x + 1, z)) x++;
                    covered.add(BoundingBox.fromCorners(hall.world(origin, start, 1, z), hall.world(origin, x, 7, z)));
                }
            }
            covered.add(BoundingBox.fromCorners(hall.world(origin, hall.width / 2 - 2, 1, 0),
                hall.world(origin, hall.width / 2 + 1, 7, 3)));
            if (hall.throughEntrance) covered.add(BoundingBox.fromCorners(
                hall.world(origin, hall.width / 2 - 2, 1, hall.depth - 4),
                hall.world(origin, hall.width / 2 + 1, 7, hall.depth - 1)));
            return List.copyOf(covered);
        }
        // These authored spans and stairways are open to the surrounding cavern.
        if (piece instanceof BridgePiece) return List.of();
        return List.of(piece.getBoundingBox());
    }

    /** Search raw terrain without requesting or generating neighboring chunks. */
    public static Optional<Structure.GenerationStub> findGenerationPoint(Structure.GenerationContext context) {
        Terrain terrain = new Terrain(context);
        int sea = context.chunkGenerator().getSeaLevel();
        int low = Math.max(context.heightAccessor().getMinY() + 8, sea + 1);
        int high = context.heightAccessor().getMaxY() - 48;
        Rotation first = Rotation.getRandom(context.random());
        for (int radius = 0; radius <= 32; radius += 8) {
            for (int dz = -radius; dz <= radius; dz += 8) for (int dx = -radius; dx <= radius; dx += 8) {
                if (radius != 0 && Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
                for (Rotation offset : Rotation.values()) {
                    Rotation rotation = first.getRotated(offset);
                    for (int y = low; y <= high; y++) {
                        BlockPos origin = new BlockPos(context.chunkPos().getBlockX(2) + dx, y,
                            context.chunkPos().getBlockZ(2) + dz);
                        // Keep the enlarged west hall and all optional branches within
                        // the start chunk's vanilla eight-chunk reference neighborhood.
                        BoundingBox envelope = BoundingBox.fromCorners(
                            origin.offset(rotate(new BlockPos(-108, 0, -96), rotation)),
                            origin.offset(rotate(new BlockPos(96, 0, 96), rotation)));
                        int chunkX = context.chunkPos().getBlockX(0), chunkZ = context.chunkPos().getBlockZ(0);
                        if (envelope.minX() < chunkX - 112 || envelope.maxX() > chunkX + 127
                                || envelope.minZ() < chunkZ - 112 || envelope.maxZ() > chunkZ + 127) continue;
                        // Four-wide natural apron beyond the gate, with walking headroom.
                        boolean walkable = true;
                        for (int x = 6; x <= 9 && walkable; x++) for (int z = -4; z <= -2; z++) {
                            BlockPos at = origin.offset(rotate(new BlockPos(x, 0, z), rotation));
                            if (!terrain.ground(at) || !terrain.air(at.above()) || !terrain.air(at.above(2))) {
                                walkable = false;
                                break;
                            }
                        }
                        if (!walkable) continue;
                        Structure.GenerationStub stub = new Structure.GenerationStub(origin,
                            builder -> generate(builder, context, terrain, origin, rotation));
                        if (context.isValidBiome(stub)) return Optional.of(stub);
                    }
                }
            }
        }
        return Optional.empty();
    }

    private static final class Terrain {
        private final Structure.GenerationContext context;
        private final Map<Long, NoiseColumn> columns = new HashMap<>();
        private final FoundationDensity foundationDensity;

        Terrain(Structure.GenerationContext context) {
            this.context = context;
            foundationDensity = new FoundationDensity(context.chunkGenerator());
        }

        private NoiseColumn column(BlockPos at) {
            long key = (long) at.getX() << 32 ^ (at.getZ() & 0xffffffffL);
            return columns.computeIfAbsent(key, _ -> context.chunkGenerator().getBaseColumn(
                at.getX(), at.getZ(), context.heightAccessor(), context.randomState()));
        }

        private BlockState state(BlockPos at) { return column(at).getBlock(at.getY()); }

        private NoiseColumn foundationColumn(BlockPos at) {
            return foundationDensity.column(() -> column(at));
        }

        boolean air(BlockPos at) { return state(at).isAir(); }

        boolean ground(BlockPos at) {
            BlockState state = state(at);
            return state.isSolidRender() && state.getFluidState().isEmpty() && !state.is(Blocks.BEDROCK);
        }

        int obstruction(List<BridgePiece> pieces) {
            int blocked = 0;
            for (BridgePiece piece : pieces) {
                BoundingBox box = piece.layoutBounds();
                // Sample the occupied height, excluding the downward foundation.
                // A shared four-block grid reuses columns across candidate layouts.
                for (int x = box.minX(); x <= box.maxX(); x += 4)
                    for (int z = box.minZ(); z <= box.maxZ(); z += 4)
                        for (int y = box.minY() + 3; y <= box.maxY(); y += 3)
                            if (!air(new BlockPos(Math.floorDiv(x, 4) * 4, y, Math.floorDiv(z, 4) * 4))) blocked++;
            }
            return blocked;
        }
    }

    /** Dense foundation surveys reuse noise tiles, but still use getBaseColumn's
     * original aquifer, material hooks and height range for every requested column.
     * The scope is confined to one survey/thread and never reads live chunks. */
    private static final class FoundationDensity {
        private final ChunkGenerator generator;
        private final Map<DensityVolume, float[]> tiles = new java.util.LinkedHashMap<>(64, .75F, true);
        private @Nullable DensitySampler sampler;

        FoundationDensity(ChunkGenerator generator) { this.generator = generator; }

        NoiseColumn column(java.util.function.Supplier<NoiseColumn> query) {
            FoundationDensity previous = FOUNDATION_DENSITY.get();
            FOUNDATION_DENSITY.set(this);
            try { return query.get(); }
            finally {
                if (previous == null) FOUNDATION_DENSITY.remove();
                else FOUNDATION_DENSITY.set(previous);
            }
        }

        ScopedDensityBuffer sample(DensitySampler.Bound bound, DensityVolume column) {
            if (sampler != bound.sampler()) {
                tiles.clear();
                sampler = bound.sampler();
            }
            var tile = new DensityVolume(8, column.sizeY(), 8,
                Math.floorDiv(column.minBlockX(), 8) * 8, column.minBlockY(),
                Math.floorDiv(column.minBlockZ(), 8) * 8);
            float[] values = tiles.get(tile);
            if (values == null) {
                values = new float[tile.size()];
                try (var density = bound.sampleVolume(tile)) {
                    for (int i = 0; i < values.length; i++) values[i] = density.get(i);
                }
                if (tiles.size() >= 64) tiles.remove(tiles.keySet().iterator().next());
                tiles.put(tile, values);
            }
            var result = bound.context().acquireBuffer(column);
            int first = tile.indexOfBlock(column.minBlockX(), column.minBlockY(), column.minBlockZ());
            for (int y = 0; y < column.sizeY(); y++) result.set(y, values[first + y]);
            return result;
        }
    }

    /** Null leaves ordinary column queries and other generators completely untouched. */
    public static @Nullable ScopedDensityBuffer foundationDensity(ChunkGenerator generator,
            DensitySampler.Bound sampler, DensityVolume column) {
        FoundationDensity active = FOUNDATION_DENSITY.get();
        if (active == null || active.generator != generator || column.sizeX() != 1 || column.sizeZ() != 1
                || column.stepBlockX() != 1 || column.stepBlockY() != 1 || column.stepBlockZ() != 1) return null;
        return active.sample(sampler, column);
    }

    private static void generate(StructurePiecesBuilder builder, Structure.GenerationContext context,
            Terrain terrain, BlockPos origin, Rotation rotation) {
        Layout best = layout(context, terrain, origin, rotation);
        for (int attempt = 1; attempt < 12 && (best.buildings().size() < 5 || best.spans() < 32 || best.loops() == 0); attempt++) {
            Layout candidate = layout(context, terrain, origin, rotation);
            if (candidate.score() > best.score()) best = candidate;
        }
        builder.clear();
        // Select once at the starting hall, before pieces cross other biomes.
        BlockPos sample = origin.offset(rotate(new BlockPos(7, 1, 7), rotation));
        Palette palette = Palette.forBiome(context.biomeResolver().getNoiseBiome(
            QuartPos.fromBlock(sample.getX()), QuartPos.fromBlock(sample.getY()), QuartPos.fromBlock(sample.getZ())));
        Map<String, Integer> wallMasks = interiorWallMasks();
        for (HallPiece hall : best.buildings()) {
            hall.palette = palette;
            hall.interior = InteriorPlan.create(hall.footprint, context.random().nextLong(), hall.width / 2 - 1,
                hall.throughEntrance, wallMasks);
            hall.foundationY = foundationBottom(hall.footprint, hall.origin(), hall.rotation,
                context.heightAccessor().getMinY(), terrain::foundationColumn);
            builder.addPiece(hall);
        }
        for (BridgePiece bridge : best.pieces()) {
            bridge.palette = palette;
            builder.addPiece(bridge);
        }
    }

    private static Layout layout(Structure.GenerationContext context, Terrain terrain, BlockPos origin, Rotation rotation) {
        StructureTemplateManager templates = context.structureTemplateManager();
        BridgeTemplate straight = BridgeTemplate.load(templates, "default");
        BridgeTemplate gate = BridgeTemplate.load(templates, "gate");
        List<BridgeTemplate> stairs = List.of(BridgeTemplate.load(templates, "stair_endstart"),
            BridgeTemplate.load(templates, "stair_end"), BridgeTemplate.load(templates, "stair_middle"),
            BridgeTemplate.load(templates, "stair_start"));
        List<BridgeTemplate> junctions = List.of(BridgeTemplate.load(templates, "corner"),
            BridgeTemplate.load(templates, "t"), BridgeTemplate.load(templates, "intersection"));
        RandomSource random = context.random();
        List<BridgePiece> pieces = new ArrayList<>();
        HallPiece building = new HallPiece(templates, origin, rotation, 16, 16);
        building.throughEntrance = true;
        List<HallPiece> buildings = new ArrayList<>();
        buildings.add(building);
        Connection mainEntrance = new Connection(new BlockPos(6, 0, 0), Direction.NORTH, ConnectionKind.ROOM)
            .transform(origin, rotation);
        pieces.addAll(attach(templates, gate, mainEntrance, 0).pieces());
        Connection rear = new Connection(new BlockPos(9, 0, 15), Direction.SOUTH, ConnectionKind.ROOM)
            .transform(origin, rotation);
        Placement threshold = attach(templates, gate, rear, 0);
        pieces.addAll(threshold.pieces());
        Placement initial = attach(templates, straight, threshold.exits().getFirst(), 0);
        pieces.addAll(initial.pieces());
        // A reserved backbone provides five buildings before optional growth.
        Placement junction = attach(templates, junctions.get(2), initial.exits().getFirst(), 0);
        pieces.addAll(junction.pieces());
        List<Connection> pending = new ArrayList<>();
        Connection west = exitFacing(junction, rotation.rotate(Direction.WEST));
        Placement largeGate = attach(templates, gate, west, 0);
        Connection door = largeGate.exits().getFirst();
        Rotation largeRotation = rotation.getRotated(Rotation.CLOCKWISE_90);
        BlockPos target = door.anchor().relative(door.outward()).relative(door.outward().getClockWise(), BRIDGE_WIDTH - 1);
        BlockPos largeOrigin = target.subtract(rotate(new BlockPos(51, 0, 0), largeRotation));
        HallPiece large = new HallPiece(templates, largeOrigin, largeRotation, 106, 106, HallFootprint.large(random));
        buildings.add(large);
        pieces.addAll(largeGate.pieces());
        Placement south = attach(templates, straight, exitFacing(junction, rotation.rotate(Direction.SOUTH)), 0);
        pieces.addAll(south.pieces());
        pending.addAll(south.exits());
        Placement east = attach(templates, straight, exitFacing(junction, rotation.rotate(Direction.EAST)), 0);
        pieces.addAll(east.pieces());
        Placement second = attach(templates, junctions.get(2), east.exits().getFirst(), 0);
        pieces.addAll(second.pieces());
        reserveSmallHall(templates, straight, gate, exitFacing(second, rotation.rotate(Direction.NORTH)), pieces, buildings);
        reserveSmallHall(templates, straight, gate, exitFacing(second, rotation.rotate(Direction.SOUTH)), pieces, buildings);
        Placement onward = attach(templates, straight, exitFacing(second, rotation.rotate(Direction.EAST)), 0);
        pieces.addAll(onward.pieces());
        Placement third = attach(templates, junctions.get(2), onward.exits().getFirst(), 0);
        pieces.addAll(third.pieces());
        reserveSmallHall(templates, straight, gate, exitFacing(third, rotation.rotate(Direction.NORTH)), pieces, buildings);
        Placement last = attach(templates, straight, exitFacing(third, rotation.rotate(Direction.SOUTH)), 0);
        pieces.addAll(last.pieces());
        pending.addAll(last.exits());
        pending.add(exitFacing(third, rotation.rotate(Direction.EAST)));
        List<Connection> terminals = new ArrayList<>();
        int spans = 8;
        int loops = 0;
        while (spans < SECTION_COUNT && !pending.isEmpty()) {
            Connection next = pending.remove(random.nextInt(pending.size()));
            List<Connection> available = new ArrayList<>(pending);
            available.addAll(terminals);
            Join join = findJoin(templates, straight, gate, next, available, pieces, buildings, origin,
                SECTION_COUNT - spans);
            if (join != null) {
                pieces.removeAll(join.removed());
                pieces.addAll(join.pieces());
                pending.remove(join.target());
                terminals.remove(join.target());
                spans += join.pieces().size() - join.removed().size();
                loops++;
                continue;
            }
            Placement preferred = null;
            int preferredCost = Integer.MAX_VALUE;
            int preferredSpans = 0;
            if (spans > 1 && spans + 4 <= SECTION_COUNT && random.nextFloat() < 0.35F) {
                Placement stairway = staircase(templates, stairs, straight, next, random);
                if (fits(pieces, stairway.pieces()) && fitsBuildings(buildings, stairway.pieces())
                        && inRange(stairway.pieces(), origin)) {
                    preferred = stairway;
                    preferredCost = terrain.obstruction(stairway.pieces());
                    preferredSpans = stairway.pieces().size();
                }
            }
            List<BridgeTemplate> choices = new ArrayList<>(junctions);
            shuffle(choices, random);
            if (spans == 1) {
                choices.remove(junctions.get(2));
                choices.addFirst(junctions.get(2));
            }
            // First try an authored junction; later extensions may also run straight.
            choices.add(spans == 1 ? choices.size() : random.nextInt(choices.size() + 1), straight);

            for (BridgeTemplate choice : choices) {
                int addedSpans = choice == straight ? 1 : choice.connections().size() - 1;
                if (spans + addedSpans > SECTION_COUNT) continue;
                List<Integer> entrances = new ArrayList<>();
                for (int index = 0; index < choice.connections().size(); index++) entrances.add(index);
                shuffle(entrances, random);
                for (int entrance : entrances) {
                    Placement first = connect(templates, choice, gate, next, entrance, random);
                    List<BridgePiece> candidates = new ArrayList<>();
                    candidates.addAll(first.pieces());
                    List<Connection> exits = new ArrayList<>();
                    for (Connection opening : first.exits()) {
                        if (choice == straight) {
                            exits.add(opening);
                        } else {
                            Placement branch = connect(templates, straight, gate, opening, 0, random);
                            candidates.addAll(branch.pieces());
                            exits.addAll(branch.exits());
                        }
                    }
                    // Accept a junction only with a full bridge span at every new opening.
                    if (!fits(pieces, candidates) || !fitsBuildings(buildings, candidates) || !inRange(candidates, origin)) continue;
                    int cost = terrain.obstruction(candidates);
                    if (cost < preferredCost) {
                        preferred = new Placement(List.copyOf(candidates), List.copyOf(exits));
                        preferredCost = cost;
                        preferredSpans = addedSpans;
                    }
                    if (preferredCost == 0) break;
                }
                if (preferredCost == 0) break;
            }
            if (preferred == null) terminals.add(next);
            else {
                pieces.addAll(preferred.pieces());
                pending.addAll(preferred.exits());
                spans += preferredSpans;
            }
        }
        terminals.addAll(pending);
        // A final pass catches endpoints created by the very last branch, even
        // when the section budget is exhausted. Adjacent ends need no new piece.
        for (int index = terminals.size() - 1; index >= 0; index--) {
            Connection next = terminals.remove(index);
            Join join = findJoin(templates, straight, gate, next, terminals, pieces, buildings, origin,
                SECTION_COUNT - spans);
            if (join == null) {
                terminals.add(index, next);
            } else {
                pieces.addAll(join.pieces());
                terminals.remove(join.target());
                spans += join.pieces().size();
                loops++;
                index = Math.min(index, terminals.size());
            }
        }
        shuffle(terminals, random);
        int buildingTarget = 5 + random.nextInt(3);
        for (Connection terminal : terminals) {
            if (buildings.size() >= buildingTarget) break;
            addBuilding(templates, gate, terminal, pieces, buildings, origin, random);
        }
        return new Layout(List.copyOf(pieces), List.copyOf(buildings), spans, loops);
    }

    private static int foundationBottom(HallFootprint footprint, BlockPos origin, Rotation rotation, int minY,
            java.util.function.Function<BlockPos, NoiseColumn> columns) {
        int top = origin.getY() - 5;
        int lowest = top;
        for (int x = 0; x < footprint.width; x++) for (int z = 0; z < footprint.depth; z++) {
            if (!footprint.contains(x, z)) continue;
            BlockPos at = origin.offset(rotate(new BlockPos(x, 0, z), rotation));
            NoiseColumn column = columns.apply(at);
            int y = top;
            while (y > minY) {
                BlockState state = column.getBlock(y);
                if (state.isSolidRender() && state.getFluidState().isEmpty()) break;
                y--;
            }
            lowest = Math.min(lowest, Math.max(minY + 1, Math.min(top, y + 1)));
        }
        return lowest;
    }

    private static Connection exitFacing(Placement placement, Direction direction) {
        return placement.exits().stream().filter(exit -> exit.outward() == direction).findFirst().orElseThrow();
    }

    private static void reserveSmallHall(StructureTemplateManager templates, BridgeTemplate straight, BridgeTemplate gate,
            Connection opening, List<BridgePiece> pieces, List<HallPiece> buildings) {
        Placement bridge = attach(templates, straight, opening, 0);
        Placement threshold = attach(templates, gate, bridge.exits().getFirst(), 0);
        Connection doorway = threshold.exits().getFirst();
        Rotation facing = Rotation.NONE;
        for (Rotation candidate : Rotation.values()) {
            if (candidate.rotate(Direction.NORTH) == doorway.outward().getOpposite()) facing = candidate;
        }
        BlockPos target = doorway.anchor().relative(doorway.outward())
            .relative(doorway.outward().getClockWise(), BRIDGE_WIDTH - 1);
        BlockPos at = target.subtract(rotate(new BlockPos(6, 0, 0), facing));
        HallPiece hall = new HallPiece(templates, at, facing, 16, 16);
        pieces.addAll(bridge.pieces());
        pieces.addAll(threshold.pieces());
        buildings.add(hall);
    }

    private static boolean meets(Connection first, Connection second) {
        return first.outward().getOpposite() == second.outward()
            && first.anchor().relative(first.outward()).relative(first.outward().getClockWise(), BRIDGE_WIDTH - 1)
                .equals(second.anchor());
    }

    private static @Nullable Join findJoin(StructureTemplateManager templates, BridgeTemplate straight,
            BridgeTemplate gate, Connection from, List<Connection> available, List<BridgePiece> placed,
            List<HallPiece> buildings, BlockPos origin, int budget) {
        Join direct = directJoin(templates, straight, gate, from, available, placed, buildings, origin, budget);
        if (direct != null || available.isEmpty()) return direct;
        for (TailAdjustment adjustment : gateAdjustments(templates, gate, from, placed)) {
            List<BridgePiece> remaining = new ArrayList<>(placed);
            remaining.removeAll(adjustment.removed());
            if (!fits(remaining, adjustment.pieces()) || !fitsBuildings(buildings, adjustment.pieces())
                    || !inRange(adjustment.pieces(), origin)) continue;
            remaining.addAll(adjustment.pieces());
            Join closure = directJoin(templates, straight, gate, adjustment.end(), available, remaining, buildings,
                origin, budget + adjustment.removed().size() - adjustment.pieces().size());
            if (closure == null) continue;
            List<BridgePiece> replacement = new ArrayList<>(adjustment.pieces());
            replacement.addAll(closure.pieces());
            return new Join(closure.target(), List.copyOf(replacement), adjustment.removed());
        }
        return null;
    }

    private record TailPart(BridgeTemplate template, int entrance) {}
    private record TailAdjustment(Connection end, List<BridgePiece> pieces, List<BridgePiece> removed) {}
    private record BridgePort(BridgePiece piece, int index, Connection connection) {}

    private static @Nullable BridgePort portAt(StructureTemplateManager templates, List<BridgePiece> pieces,
            Connection connection, boolean adjacent, List<BridgePiece> excluded) {
        for (BridgePiece piece : pieces) {
            if (excluded.contains(piece)) continue;
            List<Connection> ports = piece.ports(templates);
            for (int index = 0; index < ports.size(); index++) {
                Connection candidate = ports.get(index);
                if (adjacent ? meets(connection, candidate) : connection.equals(candidate))
                    return new BridgePort(piece, index, candidate);
            }
        }
        return null;
    }

    private static List<TailAdjustment> gateAdjustments(StructureTemplateManager templates, BridgeTemplate gate,
            Connection from, List<BridgePiece> placed) {
        List<TailAdjustment> result = new ArrayList<>();
        List<TailPart> tail = new ArrayList<>();
        List<BridgePiece> removed = new ArrayList<>();
        Connection end = from;
        for (int depth = 0; depth < 3; depth++) {
            BridgePort owner = portAt(templates, placed, end, false, removed);
            if (owner == null) break;
            BridgeTemplate part = owner.piece().description(templates);
            // Never move branches, buildings, stairs or already-connected loops.
            if (part.connections().size() != 2 || owner.piece().part().startsWith("stair_")
                    || owner.piece().part().equals("gate")) break;
            int entrance = 1 - owner.index();
            tail.add(new TailPart(part, entrance));
            removed.add(owner.piece());
            Connection back = owner.piece().ports(templates).get(entrance);
            BridgePort previous = portAt(templates, placed, back, true, removed);
            if (previous == null) break;
            if (previous.piece().part().equals("gate")) {
                Connection other = previous.piece().ports(templates).get(1 - previous.index());
                List<BridgePiece> withoutGate = new ArrayList<>(removed);
                withoutGate.add(previous.piece());
                BridgePort upstream = portAt(templates, placed, other, true, withoutGate);
                // Room and entrance gates have no bridge on their far side.
                if (upstream != null && upstream.connection().kind() == ConnectionKind.BRIDGE) {
                    result.add(rebuildTail(templates, tail, upstream.connection(), List.of(), withoutGate));
                }
                break;
            }
            Placement addedGate = attach(templates, gate, previous.connection(), 0);
            result.add(rebuildTail(templates, tail, addedGate.exits().getFirst(), addedGate.pieces(), removed));
            end = previous.connection();
        }
        return result;
    }

    private static TailAdjustment rebuildTail(StructureTemplateManager templates, List<TailPart> tail,
            Connection start, List<BridgePiece> prefix, List<BridgePiece> removed) {
        Connection end = start;
        List<BridgePiece> pieces = new ArrayList<>(prefix);
        for (int index = tail.size() - 1; index >= 0; index--) {
            TailPart part = tail.get(index);
            Placement placement = attach(templates, part.template(), end, part.entrance());
            pieces.addAll(placement.pieces());
            end = placement.exits().getFirst();
        }
        return new TailAdjustment(end, List.copyOf(pieces), List.copyOf(removed));
    }

    private static @Nullable Join directJoin(StructureTemplateManager templates, BridgeTemplate straight,
            BridgeTemplate gate, Connection from, List<Connection> available, List<BridgePiece> placed,
            List<HallPiece> buildings, BlockPos origin, int budget) {
        // Try exact seams first, then a gate, a span, and a span with one or two
        // gates. Alignment gates are deliberate, independent of the random roll.
        List<List<BridgeTemplate>> routes = List.of(List.of(), List.of(gate), List.of(straight),
            List.of(straight, gate), List.of(gate, straight), List.of(gate, straight, gate));
        for (List<BridgeTemplate> route : routes) {
            if (!route.isEmpty() && route.size() > budget) continue;
            Connection end = from;
            List<BridgePiece> candidates = new ArrayList<>();
            for (BridgeTemplate part : route) {
                Placement attached = attach(templates, part, end, 0);
                candidates.addAll(attached.pieces());
                end = attached.exits().getFirst();
            }
            for (Connection target : available) {
                if (!meets(end, target)) continue;
                if (fits(placed, candidates) && fitsBuildings(buildings, candidates) && inRange(candidates, origin)) {
                    return new Join(target, List.copyOf(candidates), List.of());
                }
            }
        }
        // A final closure may exceed the growth budget by one short piece.
        // It must match all four deck cells exactly, including height and facing.
        for (int length = 1; length <= 16; length++) {
            BridgeTemplate link = BridgeTemplate.load(templates, "link_" + length);
            Placement attached = attach(templates, link, from, 0);
            Connection end = attached.exits().getFirst();
            for (Connection target : available) {
                if (meets(end, target) && fits(placed, attached.pieces())
                        && fitsBuildings(buildings, attached.pieces()) && inRange(attached.pieces(), origin)) {
                    return new Join(target, attached.pieces(), List.of());
                }
            }
        }
        return null;
    }

    private record Join(Connection target, List<BridgePiece> pieces, List<BridgePiece> removed) {}

    private static int hallSize(RandomSource random) {
        return switch (random.nextInt(4)) {
            case 0, 1 -> 16;
            case 2 -> 46;
            default -> 76;
        };
    }

    private static boolean overlaps(BoundingBox first, BoundingBox second) {
        // Pillars and solid foundations extend below their saved boxes. Reserve
        // their horizontal footprint as well, rather than crossing a lower path.
        return first.intersects(second.minX(), second.minZ(), second.maxX(), second.maxZ());
    }

    private static boolean overlapsBuilding(BoundingBox box, HallPiece hall) {
        if (overlaps(box, hall.bodyBounds())) return true;
        if (!hall.throughEntrance) return false;
        BlockPos origin = hall.origin();
        // Keep later branches and halls off the natural walk-in apron.
        return overlaps(box, BoundingBox.fromCorners(hall.world(origin, 4, 0, -5),
            hall.world(origin, 11, 3, -1)));
    }

    private static boolean fitsBuildings(List<HallPiece> buildings, List<BridgePiece> pieces) {
        return pieces.stream().noneMatch(piece -> buildings.stream()
            .anyMatch(building -> overlapsBuilding(piece.layoutBounds(), building)));
    }

    private static boolean inRange(List<BridgePiece> pieces, BlockPos origin) {
        return pieces.stream().allMatch(piece -> inRange(piece.layoutBounds(), origin));
    }

    private static boolean inRange(BoundingBox box, BlockPos origin) {
        return box.minX() >= origin.getX() - MAX_RADIUS && box.maxX() <= origin.getX() + MAX_RADIUS
            && box.minZ() >= origin.getZ() - MAX_RADIUS && box.maxZ() <= origin.getZ() + MAX_RADIUS
            && box.minY() >= origin.getY() - 36 && box.maxY() <= origin.getY() + 40;
    }

    private static Placement staircase(StructureTemplateManager templates, List<BridgeTemplate> stairs,
            BridgeTemplate straight, Connection previous, RandomSource random) {
        boolean ascending = random.nextBoolean();
        List<BridgeTemplate> run = new ArrayList<>();
        if (random.nextBoolean()) {
            run.add(stairs.getFirst());
        } else {
            // The authored "end" is the lower landing, "start" the upper cap.
            run.add(stairs.get(ascending ? 1 : 3));
            if (random.nextBoolean()) run.add(stairs.get(2));
            run.add(stairs.get(ascending ? 3 : 1));
        }
        List<BridgePiece> pieces = new ArrayList<>();
        Connection next = previous;
        for (BridgeTemplate part : run) {
            Placement segment = attach(templates, part, next, ascending ? 0 : 1);
            pieces.addAll(segment.pieces());
            next = segment.exits().getFirst();
        }
        Placement landing = attach(templates, straight, next, 0);
        pieces.addAll(landing.pieces());
        return new Placement(List.copyOf(pieces), landing.exits());
    }

    private static boolean addBuilding(StructureTemplateManager templates, BridgeTemplate gate, Connection terminal,
            List<BridgePiece> pieces, List<HallPiece> buildings, BlockPos origin, RandomSource random) {
        Placement threshold = attach(templates, gate, terminal, 0);
        if (!fits(pieces, threshold.pieces()) || !fitsBuildings(buildings, threshold.pieces())
                || !inRange(threshold.pieces(), origin)) return false;
        Connection doorway = threshold.exits().getFirst();
        Rotation facing = Rotation.NONE;
        for (Rotation candidate : Rotation.values()) {
            if (candidate.rotate(Direction.NORTH) == doorway.outward().getOpposite()) facing = candidate;
        }
        BlockPos target = doorway.anchor().relative(doorway.outward())
            .relative(doorway.outward().getClockWise(), BRIDGE_WIDTH - 1);
        // Try varied sizes first, then the compact size before giving up this exit.
        for (int attempt = 0; attempt < 10; attempt++) {
            int width = attempt >= 8 ? 16 : hallSize(random);
            int depth = attempt >= 8 ? 16 : hallSize(random);
            BlockPos at = target.subtract(rotate(new BlockPos(width / 2 - 2, 0, 0), facing));
            HallPiece building = new HallPiece(templates, at, facing, width, depth,
                HallFootprint.plan(width, depth, random, attempt >= 8));
            BoundingBox box = building.bodyBounds();
            if (!inRange(box, origin) || pieces.stream().anyMatch(piece -> overlaps(box, piece.layoutBounds()))
                    || buildings.stream().anyMatch(other -> overlapsBuilding(box, other))) continue;
            buildings.add(building);
            pieces.addAll(threshold.pieces());
            return true;
        }
        return false;
    }

    private static <T> void shuffle(List<T> values, RandomSource random) {
        for (int index = values.size() - 1; index > 0; index--) {
            int other = random.nextInt(index + 1);
            T previous = values.set(index, values.get(other));
            values.set(other, previous);
        }
    }

    private static Placement connect(StructureTemplateManager templates, BridgeTemplate template,
            BridgeTemplate gate, Connection previous, int entranceIndex, RandomSource random) {
        Connection entrance = template.connections().get(entranceIndex);
        boolean roomBoundary = previous.kind() == ConnectionKind.BRIDGE && entrance.kind() == ConnectionKind.ROOM
            || previous.kind() == ConnectionKind.ROOM && entrance.kind() == ConnectionKind.BRIDGE;
        boolean bridgeBoundary = previous.kind() == ConnectionKind.BRIDGE && entrance.kind() == ConnectionKind.BRIDGE;
        if (!roomBoundary && (!bridgeBoundary || random.nextFloat() >= BRIDGE_GATE_CHANCE)) {
            return attach(templates, template, previous, entranceIndex);
        }
        // A gate and the piece beyond it are one candidate transaction: collision
        // rejection cannot leave an isolated gate or bypass a required room gate.
        Placement threshold = attach(templates, gate, previous, 0);
        Placement destination = attach(templates, template, threshold.exits().getFirst(), entranceIndex);
        List<BridgePiece> pieces = new ArrayList<>(threshold.pieces());
        pieces.addAll(destination.pieces());
        return new Placement(List.copyOf(pieces), destination.exits());
    }

    private static Placement attach(StructureTemplateManager templates, BridgeTemplate template,
            Connection previous, int entranceIndex) {
        Connection entrance = template.connections().get(entranceIndex);
        for (Rotation rotation : Rotation.values()) {
            if (rotation.rotate(entrance.outward()) != previous.outward().getOpposite()) continue;
            // Anchors run clockwise along each outward-facing edge. Opposing edges
            // meet in reverse order, with one block between their boundary rows.
            BlockPos target = previous.anchor().relative(previous.outward())
                .relative(previous.outward().getClockWise(), BRIDGE_WIDTH - 1);
            BlockPos origin = target.subtract(rotate(entrance.anchor(), rotation));
            List<Connection> exits = new ArrayList<>();
            for (int index = 0; index < template.connections().size(); index++) {
                if (index != entranceIndex) exits.add(template.connections().get(index).transform(origin, rotation));
            }
            return new Placement(List.of(new BridgePiece(templates, template.id(), origin, rotation)), List.copyOf(exits));
        }
        throw new IllegalStateException("Bridge connections must face horizontally");
    }

    private static boolean fits(List<BridgePiece> placed, List<BridgePiece> candidates) {
        for (int index = 0; index < candidates.size(); index++) {
            BoundingBox bounds = candidates.get(index).layoutBounds();
            for (BridgePiece piece : placed) {
                if (overlaps(bounds, piece.layoutBounds())) return false;
            }
            for (int earlier = 0; earlier < index; earlier++) {
                if (overlaps(bounds, candidates.get(earlier).layoutBounds())) return false;
            }
        }
        return true;
    }

    private static BlockPos rotate(BlockPos position, Rotation rotation) {
        return StructureTemplate.calculateRelativePosition(BridgePiece.settings(rotation), position);
    }

    // Building entrances use the same deck alignment, with a mandatory gate.
    private enum ConnectionKind { BRIDGE, GATE, ROOM }

    private record Connection(BlockPos anchor, Direction outward, ConnectionKind kind) {
        Connection(BlockPos anchor, Direction outward) {
            this(anchor, outward, ConnectionKind.BRIDGE);
        }

        Connection transform(BlockPos origin, Rotation rotation) {
            return new Connection(origin.offset(rotate(anchor, rotation)), rotation.rotate(outward), kind);
        }
    }

    private record Placement(List<BridgePiece> pieces, List<Connection> exits) {}

    private record Layout(List<BridgePiece> pieces, List<HallPiece> buildings, int spans, int loops) {
        int score() {
            return Math.min(5, buildings.size()) * 1000 + Math.min(1, loops) * 100 + spans;
        }
    }

    private record BridgeTemplate(Identifier id, List<Connection> connections) {
        static BridgeTemplate load(StructureTemplateManager templates, String part) {
            Identifier id = Naturality.id("fortress/fortress_bridge_" + part);
            StructureTemplate template = templates.get(id).orElseThrow(
                () -> new IllegalStateException("Missing fortress bridge template: " + id));
            int width = template.getSize().getX();
            int length = template.getSize().getZ();
            if (part.startsWith("stair_")) {
                int expectedWidth = switch (part) {
                    case "stair_endstart" -> 9;
                    case "stair_end" -> 10;
                    case "stair_middle", "stair_start" -> 8;
                    default -> throw new IllegalArgumentException("Unknown fortress stair: " + part);
                };
                if (width != expectedWidth || length != BRIDGE_WIDTH) {
                    throw new IllegalStateException("Invalid fortress stair dimensions: " + id);
                }
                int rise = part.equals("stair_endstart") ? 7 : 8;
                return new BridgeTemplate(id, List.of(
                    new Connection(new BlockPos(0, 0, 3), Direction.WEST),
                    new Connection(new BlockPos(width - 1, rise, 0), Direction.EAST)));
            }
            if (part.equals("gate")) {
                if (width != 1 || length != 8 || template.getSize().getY() != 10) {
                    throw new IllegalStateException("Invalid fortress gate dimensions: " + id);
                }
                // The supplied arch runs east/west, with its four-block-wide deck
                // at local Y=2 and Z=2..5. Align that deck, not the template bottom.
                return new BridgeTemplate(id, List.of(
                    new Connection(new BlockPos(0, 2, 5), Direction.WEST, ConnectionKind.GATE),
                    new Connection(new BlockPos(0, 2, 2), Direction.EAST, ConnectionKind.GATE)));
            }
            if (width < BRIDGE_WIDTH || length < (part.startsWith("link_") ? 1 : BRIDGE_WIDTH) || template.getSize().getY() <= DECK_Y
                    || part.equals("default") && width != BRIDGE_WIDTH) {
                throw new IllegalStateException("Invalid fortress bridge dimensions: " + id);
            }
            // Edge anchors describe the supplied exports, including the T's off-center stem.
            int middleX = (width - BRIDGE_WIDTH) / 2;
            int middleZ = (length - BRIDGE_WIDTH) / 2;
            List<Connection> connections = part.startsWith("link_") ? List.of(
                connection(0, 0, Direction.NORTH), connection(width - 1, length - 1, Direction.SOUTH)) : switch (part) {
                case "default" -> List.of(connection(0, 0, Direction.NORTH),
                    connection(width - 1, length - 1, Direction.SOUTH));
                case "corner" -> List.of(connection(0, 0, Direction.NORTH),
                    connection(width - 1, length - BRIDGE_WIDTH, Direction.EAST));
                case "t" -> List.of(connection(width - BRIDGE_WIDTH, 0, Direction.NORTH),
                    connection(width - 1, length - 1, Direction.SOUTH),
                    connection(0, middleZ + BRIDGE_WIDTH - 1, Direction.WEST));
                case "intersection" -> List.of(connection(middleX, 0, Direction.NORTH),
                    connection(width - 1, middleZ, Direction.EAST),
                    connection(middleX + BRIDGE_WIDTH - 1, length - 1, Direction.SOUTH),
                    connection(0, middleZ + BRIDGE_WIDTH - 1, Direction.WEST));
                default -> throw new IllegalArgumentException("Unknown fortress bridge part: " + part);
            };
            return new BridgeTemplate(id, connections);
        }

        private static Connection connection(int x, int z, Direction direction) {
            return new Connection(new BlockPos(x, DECK_Y, z), direction);
        }
    }

    private static void placePreservingLava(WorldGenLevel level, BlockPos at, BlockState state) {
        BlockState placed = BlockLavalogging.placement(state, level, at);
        if (level.setBlock(at, placed, 2) && BlockLavalogging.lavalogged(placed)) {
            level.scheduleTick(at, Fluids.LAVA, Fluids.LAVA.getTickDelay(level));
        }
    }

    /** Saved per piece; runtime processors preserve the authored states and block-entity data. */
    private enum Palette implements StructureProcessor {
        NORMAL, CRIMSON, WARPED;

        static Palette forBiome(Holder<Biome> biome) {
            if (biome.is(Biomes.CRIMSON_FOREST) || biome.is(NetherPlains.CRIMSON_PLAINS)) return CRIMSON;
            if (biome.is(Biomes.WARPED_FOREST) || biome.is(NetherPlains.WARPED_PLAINS)) return WARPED;
            return NORMAL;
        }

        static Palette load(CompoundTag tag) {
            return switch (tag.getStringOr("FortressPalette", "normal")) {
                case "crimson" -> CRIMSON;
                case "warped" -> WARPED;
                default -> NORMAL; // Earlier saves retain their original brickwork.
            };
        }

        void save(CompoundTag tag) {
            tag.putString("FortressPalette", name().toLowerCase(java.util.Locale.ROOT));
        }

        BlockState apply(BlockState state) {
            if (this == NORMAL) return state;
            Block target;
            if (state.is(Blocks.NETHER_BRICKS))
                target = this == CRIMSON ? Blocks.RED_NETHER_BRICKS : ContentRegistration.NaturalityBlocks.WARPED_NETHER_BRICKS;
            else if (state.is(Blocks.NETHER_BRICK_STAIRS))
                target = this == CRIMSON ? Blocks.RED_NETHER_BRICK_STAIRS : ContentRegistration.NaturalityBlocks.WARPED_NETHER_BRICK_STAIRS;
            else if (state.is(Blocks.NETHER_BRICK_SLAB))
                target = this == CRIMSON ? Blocks.RED_NETHER_BRICK_SLAB : ContentRegistration.NaturalityBlocks.WARPED_NETHER_BRICK_SLAB;
            else if (state.is(Blocks.NETHER_BRICK_WALL))
                target = this == CRIMSON ? Blocks.RED_NETHER_BRICK_WALL : ContentRegistration.NaturalityBlocks.WARPED_NETHER_BRICK_WALL;
            else if (state.is(Blocks.NETHER_BRICK_FENCE))
                target = this == CRIMSON ? ContentRegistration.NaturalityBlocks.CRIMSON_NETHER_BRICK_FENCE : ContentRegistration.NaturalityBlocks.WARPED_NETHER_BRICK_FENCE;
            else if (state.is(Blocks.CRACKED_NETHER_BRICKS))
                target = this == CRIMSON ? ContentRegistration.NaturalityBlocks.CRACKED_CRIMSON_NETHER_BRICKS : ContentRegistration.NaturalityBlocks.CRACKED_WARPED_NETHER_BRICKS;
            else if (state.is(Blocks.CHISELED_NETHER_BRICKS))
                target = this == CRIMSON ? ContentRegistration.NaturalityBlocks.CHISELED_CRIMSON_NETHER_BRICKS : ContentRegistration.NaturalityBlocks.CHISELED_WARPED_NETHER_BRICKS;
            else return state;
            // Includes facing, stair shape, slab type, wall connections and fluid logging.
            return target.withPropertiesOf(state);
        }

        @Override
        public StructureTemplate.@Nullable StructureBlockInfo processBlock(LevelReader level, BlockPos targetPosition,
                BlockPos referencePos, BlockPos templateRelativePos, StructureTemplate.StructureBlockInfo block,
                StructurePlaceSettings settings) {
            return new StructureTemplate.StructureBlockInfo(block.pos(), apply(block.state()), block.nbt());
        }

        @Override public MapCodec<Palette> codec() { return MapCodec.unit(this); }
    }

    /** Runtime-only settings; piece saves retain geometry, not processor state. */
    private record InteriorAir(Set<BlockPos> clearance) implements StructureProcessor {
        @Override
        public StructureTemplate.@Nullable StructureBlockInfo processBlock(LevelReader level, BlockPos targetPosition,
                BlockPos referencePos, BlockPos templateRelativePos, StructureTemplate.StructureBlockInfo block,
                StructurePlaceSettings settings) {
            // Solid authored geometry always places. Only air needs permission
            // to carve terrain: unused template padding is not an excavation box.
            return !block.state().isAir() || clearance.contains(block.pos()) ? block : null;
        }

        @Override
        public MapCodec<InteriorAir> codec() {
            return MapCodec.unit(this);
        }
    }

    /** Suppress buried internal seams and reserve the authored concave-corner patches. */
    private record HallShell(Set<BlockPos> interior, Set<BlockPos> corners, int floor, boolean corner, @Nullable Set<BlockPos> face) implements StructureProcessor {
        boolean allows(BlockPos position) {
            BlockPos column = new BlockPos(position.getX(), floor, position.getZ());
            return !interior.contains(column) && (corner || !corners.contains(column))
                && (face == null || face.contains(column));
        }

        @Override
        public StructureTemplate.@Nullable StructureBlockInfo processBlock(LevelReader level, BlockPos targetPosition,
                BlockPos referencePos, BlockPos templateRelativePos, StructureTemplate.StructureBlockInfo block,
                StructurePlaceSettings settings) {
            return allows(block.pos()) ? block : null;
        }

        @Override public MapCodec<HallShell> codec() { return MapCodec.unit(this); }
    }

    private record HallSection(int x, int z, int width, int depth) {
        boolean contains(int px, int pz) { return px >= x && pz >= z && px < x + width && pz < z + depth; }
    }

    private record HallFace(int length, int x, int z, Rotation facing, boolean startOuter, boolean endOuter) {}

    private record HallCorner(int x, int z, Rotation rotation) {}

    /** Section geometry is persisted explicitly so later additions cannot reshuffle saved halls. */
    private record InteriorRoom(int shape, int variant, int x, int z, Rotation rotation, Mirror mirror, int wallMask) {
        private static final List<List<BlockPos>> SHAPES = List.of(
            List.of(BlockPos.ZERO),
            List.of(BlockPos.ZERO, new BlockPos(1, 0, 0)),
            List.of(BlockPos.ZERO, new BlockPos(1, 0, 0), new BlockPos(0, 0, 1), new BlockPos(1, 0, 1)),
            List.of(BlockPos.ZERO, new BlockPos(1, 0, 0), new BlockPos(0, 0, 1)),
            List.of(BlockPos.ZERO, new BlockPos(1, 0, 0), new BlockPos(2, 0, 0), new BlockPos(1, 0, 1)));
        private static final String[] NAMES = {"single", "double", "square", "l", "t"};

        String name() { return NAMES[shape] + "_" + (variant < 10 ? "0" : "") + variant; }

        BlockPos transform(BlockPos position) {
            BlockPos mirrored = switch (mirror) {
                case FRONT_BACK -> new BlockPos(-position.getX(), position.getY(), position.getZ());
                case LEFT_RIGHT -> new BlockPos(position.getX(), position.getY(), -position.getZ());
                default -> position;
            };
            return rotate(mirrored, rotation);
        }

        BlockPos offset() {
            List<BlockPos> cells = SHAPES.get(shape);
            int width = (cells.stream().mapToInt(BlockPos::getX).max().orElseThrow() + 1) * 9 - 1;
            int depth = (cells.stream().mapToInt(BlockPos::getZ).max().orElseThrow() + 1) * 9 - 1;
            BlockPos far = transform(new BlockPos(width - 1, 0, depth - 1));
            return new BlockPos(x - Math.min(0, far.getX()), 0, z - Math.min(0, far.getZ()));
        }

        Set<BlockPos> columns() {
            Set<BlockPos> columns = new LinkedHashSet<>();
            List<BlockPos> cells = SHAPES.get(shape);
            BlockPos offset = offset();
            for (BlockPos cell : cells) {
                boolean east = cells.contains(cell.east()), south = cells.contains(cell.south());
                for (int dx = 0; dx < (east ? 9 : 8); dx++) for (int dz = 0; dz < (south ? 9 : 8); dz++) {
                    if (dx == 8 && dz == 8 && !cells.contains(cell.east().south())) continue;
                    columns.add(offset.offset(transform(new BlockPos(cell.getX() * 9 + dx, 0, cell.getZ() * 9 + dz))));
                }
            }
            return columns;
        }

        Set<BlockPos> requiredWalls() {
            if (wallMask == 0) return Set.of();
            InteriorRoom source = new InteriorRoom(shape, variant, 0, 0, Rotation.NONE, Mirror.NONE, 0);
            Set<BlockPos> columns = source.columns(), walls = new LinkedHashSet<>();
            int maxX = columns.stream().mapToInt(BlockPos::getX).max().orElseThrow();
            int maxZ = columns.stream().mapToInt(BlockPos::getZ).max().orElseThrow();
            BlockPos offset = offset();
            for (BlockPos column : columns) {
                // Bit 16 persists the new concave-side rule without changing old saved routes.
                if ((wallMask & 16) != 0 && shape >= 3) {
                    for (Direction direction : Direction.Plane.HORIZONTAL) {
                        BlockPos adjacent = column.relative(direction);
                        if (!columns.contains(adjacent) && adjacent.getX() >= 0 && adjacent.getX() <= maxX
                                && adjacent.getZ() >= 0 && adjacent.getZ() <= maxZ)
                            walls.add(offset.offset(transform(adjacent)));
                    }
                }
                if ((wallMask & 1) != 0 && column.getZ() == 0) walls.add(offset.offset(transform(column.north())));
                if ((wallMask & 2) != 0 && column.getX() == maxX) walls.add(offset.offset(transform(column.east())));
                if ((wallMask & 4) != 0 && column.getZ() == maxZ) walls.add(offset.offset(transform(column.south())));
                if ((wallMask & 8) != 0 && column.getX() == 0) walls.add(offset.offset(transform(column.west())));
            }
            return walls;
        }

        boolean blocksEntrance(int entranceX, int depth, boolean throughEntrance) {
            for (BlockPos at : requiredWalls()) {
                if (at.getX() >= entranceX - 1 && at.getX() <= entranceX + 2
                        && (at.getZ() == 3 || throughEntrance && at.getZ() == depth - 4)) return true;
            }
            return false;
        }
    }

    private record InteriorWall(int x, int z, int length, boolean alongZ, String template) {}
    private record WallBoundary(boolean alongZ, int first, int second) {}

    private record InteriorPassage(BlockPos start, int length, boolean alongZ, int first, int second) {
        int other(int region) { return first == region ? second : first; }
        Direction direction() { return alongZ ? Direction.SOUTH : Direction.EAST; }
        Direction travel(int region) {
            Direction forward = alongZ ? Direction.EAST : Direction.SOUTH;
            return region == first ? forward : forward.getOpposite();
        }
    }

    /** Grid dividers also reach the facade through the perimeter strips, preventing bypasses. */
    private static final class InteriorLayout {
        final int[][] regions;
        final Set<BlockPos> walls = new LinkedHashSet<>();
        final Set<BlockPos> requiredWalls = new LinkedHashSet<>();
        final List<Integer> areas = new ArrayList<>(List.of(0));
        final List<InteriorPassage> passages = new ArrayList<>();
        final List<List<Integer>> neighbors = new ArrayList<>();

        InteriorLayout(HallFootprint footprint, List<InteriorRoom> rooms) {
            regions = new int[footprint.width][footprint.depth];
            Set<BlockPos> authored = new LinkedHashSet<>();
            for (InteriorRoom room : rooms) {
                authored.addAll(room.columns());
                for (BlockPos at : room.requiredWalls()) if (footprint.interior(at.getX(), at.getZ())) requiredWalls.add(at);
            }
            for (int x = 0; x < footprint.width; x++) for (int z = 0; z < footprint.depth; z++) {
                BlockPos column = new BlockPos(x, 0, z);
                if (footprint.interior(x, z) && !authored.contains(column)
                        && (Math.floorMod(x - 3, 9) == 0 || Math.floorMod(z - 3, 9) == 0)) walls.add(column);
            }
            walls.addAll(requiredWalls);
            for (int z = 0; z < footprint.depth; z++) for (int x = 0; x < footprint.width; x++) {
                BlockPos start = new BlockPos(x, 0, z);
                if (!footprint.interior(x, z) || walls.contains(start) || regions[x][z] != 0) continue;
                int region = areas.size(), area = 0;
                java.util.ArrayDeque<BlockPos> queue = new java.util.ArrayDeque<>();
                queue.add(start);
                regions[x][z] = region;
                while (!queue.isEmpty()) {
                    BlockPos at = queue.removeFirst(); area++;
                    for (Direction direction : Direction.Plane.HORIZONTAL) {
                        BlockPos next = at.relative(direction);
                        if (!footprint.interior(next.getX(), next.getZ()) || walls.contains(next)
                                || regions[next.getX()][next.getZ()] != 0) continue;
                        regions[next.getX()][next.getZ()] = region;
                        queue.addLast(next);
                    }
                }
                areas.add(area);
            }
            Map<BlockPos, WallBoundary> boundaries = new java.util.LinkedHashMap<>();
            for (BlockPos column : walls) {
                int x = column.getX(), z = column.getZ();
                int west = region(x - 1, z), east = region(x + 1, z), north = region(x, z - 1), south = region(x, z + 1);
                if (west > 0 && east > 0 && west != east) boundaries.put(column, new WallBoundary(true, west, east));
                else if (north > 0 && south > 0 && north != south) boundaries.put(column, new WallBoundary(false, north, south));
            }
            for (int i = 0; i < areas.size(); i++) neighbors.add(new ArrayList<>());
            Set<BlockPos> visited = new LinkedHashSet<>();
            for (Map.Entry<BlockPos, WallBoundary> entry : boundaries.entrySet()) {
                BlockPos start = entry.getKey();
                if (visited.contains(start)) continue;
                WallBoundary boundary = entry.getValue();
                Direction along = boundary.alongZ() ? Direction.SOUTH : Direction.EAST;
                while (boundary.equals(boundaries.get(start.relative(along.getOpposite())))) start = start.relative(along.getOpposite());
                int length = 0;
                boolean required = false;
                while (boundary.equals(boundaries.get(start.relative(along, length)))) {
                    required |= requiredWalls.contains(start.relative(along, length));
                    visited.add(start.relative(along, length)); length++;
                }
                if (length < 2 || required) continue;
                int passage = passages.size();
                passages.add(new InteriorPassage(start, length, boundary.alongZ(), boundary.first(), boundary.second()));
                neighbors.get(boundary.first()).add(passage);
                neighbors.get(boundary.second()).add(passage);
            }
        }

        int region(int x, int z) {
            return x < 0 || z < 0 || x >= regions.length || z >= regions[0].length ? 0 : regions[x][z];
        }
    }

    /** A simple path, not a spanning tree: every visited region has at most two route connections. */
    private record InteriorRoute(List<Integer> regions, List<Integer> passages, Set<Integer> hallways) {
        static InteriorRoute create(InteriorLayout layout, int entranceX, RandomSource random) {
            int start = layout.region(entranceX, 4);
            if (start == 0) throw new IllegalArgumentException("Fortress entrance has no interior region");
            RouteSearch search = new RouteSearch(layout, start, random);
            search.find();
            List<Integer> path = search.bestRegions, connections = search.bestPassages;
            Set<Integer> hallways = new LinkedHashSet<>();
            for (int first = 0; first < connections.size();) {
                int end = first + 1;
                Direction travel = layout.passages.get(connections.get(first)).travel(path.get(first));
                // A straight hallway can be any length. Adjacent chosen runs merge through turns.
                while (end < connections.size() && layout.passages.get(connections.get(end)).travel(path.get(end)) == travel) end++;
                if (random.nextInt(3) != 0) for (int i = first; i < end; i++) hallways.add(connections.get(i));
                first = end;
            }
            Set<Integer> hallwayRegions = new LinkedHashSet<>();
            for (int index : hallways) {
                InteriorPassage passage = layout.passages.get(index);
                hallwayRegions.add(passage.first()); hallwayRegions.add(passage.second());
            }
            for (int index : connections) {
                InteriorPassage passage = layout.passages.get(index);
                if (hallwayRegions.contains(passage.first()) && hallwayRegions.contains(passage.second())) hallways.add(index);
            }
            return new InteriorRoute(List.copyOf(path), List.copyOf(connections), Set.copyOf(hallways));
        }

        void validate(InteriorLayout layout) {
            if (regions.isEmpty() || passages.size() != regions.size() - 1 || new java.util.HashSet<>(regions).size() != regions.size())
                throw new IllegalArgumentException("Invalid fortress interior route");
            for (int region : regions) if (region <= 0 || region >= layout.areas.size())
                throw new IllegalArgumentException("Invalid fortress route region");
            for (int i = 0; i < passages.size(); i++) {
                int index = passages.get(i);
                if (index < 0 || index >= layout.passages.size()) throw new IllegalArgumentException("Invalid fortress passage");
                InteriorPassage passage = layout.passages.get(index);
                int first = regions.get(i), second = regions.get(i + 1);
                if (!(passage.first() == first && passage.second() == second || passage.second() == first && passage.first() == second))
                    throw new IllegalArgumentException("Disconnected fortress route");
            }
            if (!passages.containsAll(hallways)) throw new IllegalArgumentException("Hallway outside fortress route");
        }
    }

    private static final class RouteSearch {
        private final InteriorLayout layout;
        private final int start;
        private final RandomSource random;
        private final boolean[] visited;
        private final List<Integer> path = new ArrayList<>(), connections = new ArrayList<>();
        private List<Integer> bestRegions = List.of(), bestPassages = List.of();
        private int bestArea, budget;

        RouteSearch(InteriorLayout layout, int start, RandomSource random) {
            this.layout = layout; this.start = start; this.random = random;
            visited = new boolean[layout.areas.size()];
        }

        void find() {
            // Fixed work budget keeps irregular floor plans from causing unbounded worldgen searches.
            for (int attempt = 0; attempt < 8 && bestRegions.size() < layout.areas.size() - 1; attempt++) {
                budget = 8000;
                visit(start, 0);
            }
        }

        private void visit(int region, int area) {
            if (--budget < 0) return;
            visited[region] = true; path.add(region); area += layout.areas.get(region);
            if (area > bestArea) {
                bestArea = area; bestRegions = List.copyOf(path); bestPassages = List.copyOf(connections);
            }
            if (bestRegions.size() < layout.areas.size() - 1) {
                List<Integer> candidates = new ArrayList<>();
                for (int passage : layout.neighbors.get(region)) if (!visited[layout.passages.get(passage).other(region)]) candidates.add(passage);
                // Randomized low-degree-first exploration favors long routes rather than early dead ends.
                while (!candidates.isEmpty() && budget > 0) {
                    int chosen = random.nextInt(candidates.size()), degree = Integer.MAX_VALUE, ties = 0;
                    for (int i = 0; i < candidates.size(); i++) {
                        int neighbor = layout.passages.get(candidates.get(i)).other(region), count = 0;
                        for (int edge : layout.neighbors.get(neighbor)) if (!visited[layout.passages.get(edge).other(neighbor)]) count++;
                        if (count == 0 && candidates.size() > 1) continue;
                        if (count < degree) { chosen = i; degree = count; ties = 1; }
                        else if (count == degree && random.nextInt(++ties) == 0) chosen = i;
                    }
                    int passage = candidates.remove(chosen);
                    connections.add(passage);
                    visit(layout.passages.get(passage).other(region), area);
                    connections.removeLast();
                }
            }
            path.removeLast(); visited[region] = false;
        }
    }

    private static final class InteriorPlan {
        final long seed;
        final List<InteriorRoom> rooms;
        private @Nullable InteriorRoute route;
        private @Nullable InteriorLayout cachedLayout;

        InteriorPlan(long seed, List<InteriorRoom> rooms) { this.seed = seed; this.rooms = List.copyOf(rooms); }

        synchronized InteriorLayout layout(HallFootprint footprint) {
            InteriorLayout layout = cachedLayout;
            if (layout == null) {
                layout = new InteriorLayout(footprint, rooms);
                cachedLayout = layout;
            }
            return layout;
        }

        static InteriorPlan create(HallFootprint footprint, long seed, int entranceX,
                boolean throughEntrance, Map<String, Integer> wallMasks) {
            RandomSource random = RandomSource.create(seed);
            InteriorPlan best = candidate(footprint, seed, entranceX, throughEntrance, wallMasks, random);
            double coverage = best.coverage(footprint);
            for (int attempt = 1; attempt < 4 && coverage < 0.98; attempt++) {
                InteriorPlan candidate = candidate(footprint, seed, entranceX, throughEntrance, wallMasks, random);
                double next = candidate.coverage(footprint);
                if (next > coverage) { best = candidate; coverage = next; }
            }
            return best;
        }

        private double coverage(HallFootprint footprint) {
            InteriorRoute route = this.route;
            if (route == null) return 0.0;
            InteriorLayout layout = layout(footprint);
            int covered = 0, total = 0;
            for (int area : layout.areas) total += area;
            for (int region : route.regions()) covered += layout.areas.get(region);
            return total == 0 ? 0.0 : (double) covered / total;
        }

        private static InteriorPlan candidate(HallFootprint footprint, long seed, int entranceX,
                boolean throughEntrance, Map<String, Integer> wallMasks, RandomSource random) {
            Set<BlockPos> occupied = new LinkedHashSet<>();
            Set<BlockPos> reservedWalls = new LinkedHashSet<>();
            Map<BlockPos, String> roomNames = new HashMap<>();
            List<InteriorRoom> rooms = new ArrayList<>();
            for (int z = 4; z + 7 < footprint.depth; z += 9) for (int x = 4; x + 7 < footprint.width; x += 9) {
                BlockPos first = new BlockPos(x, 0, z);
                if (occupied.contains(first) || !footprint.interior(x, z)) continue;
                List<Integer> choices = new ArrayList<>();
                for (int choice = 0; choice < 60; choice++) choices.add(choice);
                while (!choices.isEmpty()) {
                    int choice = choices.remove(random.nextInt(choices.size())), shape = choice / 12;
                    Rotation rotation = Rotation.values()[choice % 4];
                    Mirror mirror = Mirror.values()[choice / 4 % 3];
                    InteriorRoom prototype = new InteriorRoom(shape, 1, 0, 0, rotation, mirror, 0);
                    Set<BlockPos> local = prototype.columns();
                    // Anchor the first occupied cell, including rotations whose bounding corner is empty.
                    BlockPos anchor = local.stream().min(java.util.Comparator.<BlockPos>comparingInt(BlockPos::getZ)
                        .thenComparingInt(BlockPos::getX)).orElseThrow();
                    InteriorRoom unmarked = new InteriorRoom(shape, 1 + random.nextInt(shape == 0 ? 20 : 4),
                        x - anchor.getX(), z - anchor.getZ(), rotation, mirror, 0);
                    InteriorRoom room = new InteriorRoom(shape, unmarked.variant(), unmarked.x(), unmarked.z(), rotation, mirror,
                        wallMasks.getOrDefault(unmarked.name(), 0) | 16);
                    if (room.blocksEntrance(entranceX, footprint.depth, throughEntrance)) continue;
                    Set<BlockPos> columns = room.columns();
                    Set<BlockPos> required = room.requiredWalls();
                    if (columns.stream().anyMatch(p -> !footprint.interior(p.getX(), p.getZ()) || occupied.contains(p) || reservedWalls.contains(p))
                            || required.stream().anyMatch(occupied::contains)) continue;
                    // Compare identity before transforms, across the one-block partition seam.
                    boolean repeated = false;
                    for (BlockPos column : columns) {
                        for (Direction direction : Direction.Plane.HORIZONTAL) {
                            if (room.name().equals(roomNames.get(column.relative(direction)))
                                    || room.name().equals(roomNames.get(column.relative(direction, 2)))) repeated = true;
                        }
                        if (repeated) break;
                    }
                    if (repeated) continue;
                    rooms.add(room);
                    for (BlockPos column : columns) roomNames.put(column, room.name());
                    occupied.addAll(columns);
                    reservedWalls.addAll(required);
                    break;
                }
            }
            InteriorPlan plan = new InteriorPlan(seed, rooms);
            plan.route = InteriorRoute.create(plan.layout(footprint), entranceX, random);
            return plan;
        }

        List<Integer> save() {
            List<Integer> values = new ArrayList<>();
            for (InteriorRoom room : rooms) {
                values.add(room.shape()); values.add(room.variant()); values.add(room.x());
                values.add(room.z()); values.add(room.rotation().ordinal());
                values.add(room.mirror().ordinal());
                values.add(room.wallMask());
            }
            return values;
        }

        static InteriorPlan load(long seed, List<Integer> values, int version) {
            int stride = version == 1 ? 5 : version < 4 ? 6 : 7;
            if (version < 1 || version > 5 || values.size() % stride != 0 || values.size() > 1400)
                throw new IllegalArgumentException("Invalid fortress interior plan");
            List<InteriorRoom> rooms = new ArrayList<>();
            for (int i = 0; i < values.size(); i += stride) {
                int shape = values.get(i), variant = values.get(i + 1), x = values.get(i + 2), z = values.get(i + 3), rotation = values.get(i + 4);
                int mirror = version == 1 ? 0 : values.get(i + 5);
                int wallMask = version < 4 ? 0 : values.get(i + 6);
                if (shape < 0 || shape > 4 || variant < 1 || variant > (shape == 0 ? 20 : 4)
                        || x < 0 || z < 0 || x > 196 || z > 196 || rotation < 0 || rotation > 3 || mirror < 0 || mirror > 2
                        || wallMask < 0 || wallMask > (version < 5 ? 15 : 31))
                    throw new IllegalArgumentException("Invalid fortress interior room");
                rooms.add(new InteriorRoom(shape, variant, x, z, Rotation.values()[rotation], Mirror.values()[mirror], wallMask));
            }
            return new InteriorPlan(seed, rooms);
        }
    }

    /** Full authored sections take priority over the floor/roof strips around irregular outlines. */
    private record InteriorFill(Set<BlockPos> remaining, int floor) implements StructureProcessor {
        @Override
        public StructureTemplate.@Nullable StructureBlockInfo processBlock(LevelReader level, BlockPos targetPosition,
                BlockPos referencePos, BlockPos templateRelativePos, StructureTemplate.StructureBlockInfo block,
                StructurePlaceSettings settings) {
            return (block.pos().getY() == floor || block.pos().getY() == floor + 7)
                && remaining.contains(new BlockPos(block.pos().getX(), floor, block.pos().getZ())) ? block : null;
        }
        @Override public MapCodec<InteriorFill> codec() { return MapCodec.unit(this); }
    }

    private record InteriorPartition(Set<BlockPos> openings, Set<BlockPos> posts, int floor) implements StructureProcessor {
        @Override
        public StructureTemplate.@Nullable StructureBlockInfo processBlock(LevelReader level, BlockPos targetPosition,
                BlockPos referencePos, BlockPos templateRelativePos, StructureTemplate.StructureBlockInfo block,
                StructurePlaceSettings settings) {
            BlockPos column = new BlockPos(block.pos().getX(), floor, block.pos().getZ());
            if (posts.contains(column)) return null;
            if (openings.contains(column) && block.pos().getY() > floor && block.pos().getY() <= floor + 4)
                return new StructureTemplate.StructureBlockInfo(block.pos(), Blocks.AIR.defaultBlockState(), null);
            int height = block.pos().getY() - floor;
            boolean opening = openings.contains(column);
            boolean jamb = false;
            for (Direction direction : Direction.Plane.HORIZONTAL)
                if (openings.contains(column.relative(direction))) jamb = true;
            // Keep the two-by-four opening; frame it only up to its lintel.
            if ((opening && height == 5) || (jamb && height >= 1 && height <= 5))
                return new StructureTemplate.StructureBlockInfo(block.pos(), Blocks.NETHER_BRICKS.defaultBlockState(), null);
            if (block.state().getBlock() instanceof WallBlock) {
                // The partition template runs along local X. Its endpoint states must not
                // introduce posts when columns are repeated or clipped at chunk boundaries.
                BlockState flat = block.state().setValue(WallBlock.UP, false)
                    .setValue(WallBlock.NORTH, WallSide.NONE).setValue(WallBlock.SOUTH, WallSide.NONE)
                    .setValue(WallBlock.EAST, WallSide.TALL).setValue(WallBlock.WEST, WallSide.TALL);
                return new StructureTemplate.StructureBlockInfo(block.pos(), flat, block.nbt());
            }
            return block;
        }
        @Override public MapCodec<InteriorPartition> codec() { return MapCodec.unit(this); }
    }

    /** Union of authored rectangles; 30-block offsets preserve both facade rhythms. */
    private static final class HallFootprint {
        final List<HallSection> sections;
        final List<HallCorner> corners = new ArrayList<>();
        final List<HallFace> faces = new ArrayList<>();
        final int width;
        final int depth;
        private final int[][] area;
        private final boolean[][] core;
        private final boolean[][] interior;

        HallFootprint(List<HallSection> sections) {
            if (sections.isEmpty() || sections.size() > 8) throw new IllegalArgumentException("Invalid hall sections");
            this.sections = List.copyOf(sections);
            for (HallSection section : sections) {
                if (section.x() < 0 || section.z() < 0 || section.x() > 90 || section.z() > 90
                        || !validSize(section.width()) || !validSize(section.depth())) {
                    throw new IllegalArgumentException("Invalid hall section dimensions");
                }
            }
            width = sections.stream().mapToInt(section -> section.x() + section.width()).max().orElseThrow();
            depth = sections.stream().mapToInt(section -> section.z() + section.depth()).max().orElseThrow();
            // Trace the union's actual perimeter. Rectangle endpoints inside a
            // longer wall must never contribute outer caps or restart its pattern.
            for (Rotation facing : Rotation.values()) {
                Direction outward = facing.rotate(Direction.NORTH);
                Direction along = outward.getClockWise();
                for (int x = 0; x < width; x++) for (int z = 0; z < depth; z++) {
                    BlockPos first = new BlockPos(x, 0, z);
                    if (!boundary(first, outward) || boundary(first.relative(along.getOpposite()), outward)) continue;
                    int length = 1;
                    while (boundary(first.relative(along, length), outward)) length++;
                    BlockPos before = first.relative(along.getOpposite());
                    BlockPos after = first.relative(along, length);
                    faces.add(new HallFace(length, x, z, facing, !contains(before.getX(), before.getZ()),
                        !contains(after.getX(), after.getZ())));
                }
            }
            area = new int[width + 1][depth + 1];
            for (int x = 0; x < width; x++) for (int z = 0; z < depth; z++) {
                area[x + 1][z + 1] = area[x][z + 1] + area[x + 1][z] - area[x][z] + (contains(x, z) ? 1 : 0);
            }
            core = new boolean[width][depth];
            interior = new boolean[width][depth];
            for (int x = 0; x < width; x++) for (int z = 0; z < depth; z++) {
                core[x][z] = inset(x, z, 3);
                interior[x][z] = inset(x, z, 4);
            }
            for (int x = 0; x <= width; x++) for (int z = 0; z <= depth; z++) {
                boolean[] quadrants = {contains(x - 1, z - 1), contains(x, z - 1), contains(x, z), contains(x - 1, z)};
                int occupied = 0, missing = 0;
                for (int i = 0; i < 4; i++) { if (quadrants[i]) occupied++; else missing = i; }
                if (occupied == 3) corners.add(new HallCorner(x, z, Rotation.values()[(missing + 3) % 4]));
            }
        }

        private boolean boundary(BlockPos at, Direction outward) {
            BlockPos outside = at.relative(outward);
            return contains(at.getX(), at.getZ()) && !contains(outside.getX(), outside.getZ());
        }

        boolean ownsEdge(int index, int along) { return along >= 0 && along < faces.get(index).length(); }

        private static boolean validSize(int size) { return size == 16 || size == 46 || size == 76 || size == 106; }

        boolean contains(int x, int z) {
            for (HallSection section : sections) if (section.contains(x, z)) return true;
            return false;
        }

        private boolean inset(int x, int z, int margin) {
            int left = x - margin, top = z - margin, right = x + margin + 1, bottom = z + margin + 1;
            if (left < 0 || top < 0 || right > width || bottom > depth) return false;
            int filled = area[right][bottom] - area[left][bottom] - area[right][top] + area[left][top];
            return filled == (right - left) * (bottom - top);
        }

        boolean core(int x, int z) { return x >= 0 && z >= 0 && x < width && z < depth && core[x][z]; }
        boolean interior(int x, int z) { return x >= 0 && z >= 0 && x < width && z < depth && interior[x][z]; }

        List<Integer> save() {
            List<Integer> packed = new ArrayList<>();
            for (HallSection section : sections) {
                packed.add(section.x()); packed.add(section.z()); packed.add(section.width()); packed.add(section.depth());
            }
            return List.copyOf(packed);
        }

        static HallFootprint load(List<Integer> packed, int width, int depth) {
            if (packed.isEmpty()) return new HallFootprint(List.of(new HallSection(0, 0, width, depth)));
            if (packed.size() % 4 != 0) throw new IllegalArgumentException("Invalid saved hall footprint");
            List<HallSection> sections = new ArrayList<>();
            for (int i = 0; i < packed.size(); i += 4) {
                sections.add(new HallSection(packed.get(i), packed.get(i + 1), packed.get(i + 2), packed.get(i + 3)));
            }
            return new HallFootprint(sections);
        }

        static HallFootprint large(RandomSource random) {
            List<HallSection> sections = new ArrayList<>(List.of(
                new HallSection(0, 0, 106, 46), new HallSection(0, 60, 106, 46)));
            int hallX = random.nextInt(4) * 30;
            sections.add(new HallSection(hallX, 30, 16, 46));
            if (random.nextBoolean()) sections.add(new HallSection(hallX == 0 ? 90 : 0, 30, 16, 46));
            return new HallFootprint(sections);
        }

        static HallFootprint plan(int width, int depth, RandomSource random, boolean compact) {
            List<HallSection> sections = new ArrayList<>();
            sections.add(new HallSection(0, 0, width, depth));
            if (!compact) {
                int shape = random.nextInt(5);
                int side = width >= 46 && random.nextBoolean() ? 30 : 0;
                if (shape != 0) sections.add(new HallSection(side, Math.max(0, depth - 16), 16, 46));
                if (shape >= 2) sections.add(new HallSection(0, depth + 14, 46, 16));
                if (shape >= 3 && width >= 46) sections.add(new HallSection(30 - side, Math.max(0, depth - 16), 16, 46));
                if (shape == 4) sections.add(new HallSection(0, depth + 14, 46, 46));
            }
            // Tall roots already occupy the available footprint; retain their rectangle.
            if (sections.stream().anyMatch(section -> section.x() > 60 || section.z() > 60))
                sections = List.of(new HallSection(0, 0, width, depth));
            return new HallFootprint(sections);
        }
    }

    /** The authored one-storey facade, with independent 5/6-block horizontal
     * rhythms and a solid core behind the downward-extending exterior relief. */
    private static final class HallPiece extends StructurePiece {
        private Palette palette = Palette.NORMAL;
        private @Nullable InteriorPlan interior;
        private final StructureTemplateManager templates;
        private final Rotation rotation;
        private final int width;
        private final int depth;
        private boolean throughEntrance;
        private final HallFootprint footprint;
        private volatile int foundationY = Integer.MIN_VALUE;

        HallPiece(StructureTemplateManager templates, BlockPos origin, Rotation rotation, int width, int depth) {
            this(templates, origin, rotation, width, depth,
                new HallFootprint(List.of(new HallSection(0, 0, width, depth))));
        }

        HallPiece(StructureTemplateManager templates, BlockPos origin, Rotation rotation, int width, int depth,
                HallFootprint footprint) {
            super(HALL_TYPE, 0, hallBounds(origin, rotation, footprint.width, footprint.depth));
            this.footprint = footprint;
            this.templates = templates;
            this.rotation = rotation;
            this.width = width;
            this.depth = depth;
            setOrientation(null);
        }

        HallPiece(StructureTemplateManager templates, CompoundTag tag) {
            super(HALL_TYPE, tag);
            palette = Palette.load(tag);
            if (tag.contains("InteriorRooms")) interior = InteriorPlan.load(tag.getLongOr("InteriorSeed", 0L),
                tag.read("InteriorRooms", Codec.INT.listOf()).orElseThrow(), tag.getIntOr("InteriorVersion", 1));
            this.templates = templates;
            rotation = tag.read("HallRotation", Rotation.CODEC).orElseThrow();
            width = tag.getIntOr("HallWidth", 46);
            depth = tag.getIntOr("HallDepth", 46);
            throughEntrance = tag.getBooleanOr("ThroughEntrance", false);
            foundationY = tag.getIntOr("HallFoundationY", Integer.MIN_VALUE);
            footprint = HallFootprint.load(tag.read("HallSections", Codec.INT.listOf()).orElse(List.of()), width, depth);
            InteriorPlan savedPlan = interior;
            if (savedPlan != null && tag.contains("InteriorRoute")) {
                savedPlan.route = new InteriorRoute(tag.read("InteriorRoute", Codec.INT.listOf()).orElseThrow(),
                    tag.read("InteriorPassages", Codec.INT.listOf()).orElseThrow(),
                    Set.copyOf(tag.read("InteriorHallways", Codec.INT.listOf()).orElseThrow()));
                savedPlan.route.validate(savedPlan.layout(footprint));
            }
            if ((width != 16 && width != 46 && width != 76 && width != 106)
                    || (depth != 16 && depth != 46 && depth != 76 && depth != 106)) {
                throw new IllegalArgumentException("Invalid fortress hall dimensions");
            }
            setOrientation(null);
        }

        private static BoundingBox hallBounds(BlockPos origin, Rotation rotation, int width, int depth) {
            return BoundingBox.fromCorners(origin.offset(rotate(new BlockPos(0, -4, 0), rotation)),
                origin.offset(rotate(new BlockPos(width - 1, 9, depth - 1), rotation)));
        }

        private BlockPos origin() {
            BoundingBox local = hallBounds(BlockPos.ZERO, rotation, footprint.width, footprint.depth);
            return new BlockPos(boundingBox.minX() - local.minX(), boundingBox.minY() - local.minY(),
                boundingBox.minZ() - local.minZ());
        }

        BoundingBox bodyBounds() {
            return boundingBox;
        }

        @Override
        protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
            tag.store("HallRotation", Rotation.CODEC, rotation);
            palette.save(tag);
            InteriorPlan plan = interior;
            if (plan != null) {
                tag.putLong("InteriorSeed", plan.seed);
                InteriorRoute route = plan.route;
                tag.putInt("InteriorVersion", 5);
                if (route != null) {
                    tag.store("InteriorRoute", Codec.INT.listOf(), route.regions());
                    tag.store("InteriorPassages", Codec.INT.listOf(), route.passages());
                    tag.store("InteriorHallways", Codec.INT.listOf(), List.copyOf(route.hallways()));
                }
                tag.store("InteriorRooms", Codec.INT.listOf(), plan.save());
            }
            tag.putInt("HallWidth", width);
            tag.putInt("HallDepth", depth);
            tag.putBoolean("ThroughEntrance", throughEntrance);
            tag.store("HallSections", Codec.INT.listOf(), footprint.save());
            if (foundationY != Integer.MIN_VALUE) tag.putInt("HallFoundationY", foundationY);
        }

        private StructureTemplate template(String name) {
            Identifier id = Naturality.id("fortress/fortress_hall_" + name);
            return templates.get(id).orElseThrow(() -> new IllegalStateException("Missing fortress hall template: " + id));
        }

        private void interiorPart(String name, BlockPos local, Rotation facing, WorldGenLevel level,
                BoundingBox clip, RandomSource random, @Nullable StructureProcessor processor) {
            interiorPart(name, local, facing, Mirror.NONE, level, clip, random, processor);
        }

        private void interiorPart(String name, BlockPos local, Rotation facing, Mirror mirror, WorldGenLevel level,
                BoundingBox clip, RandomSource random, @Nullable StructureProcessor processor) {
            Identifier id = Naturality.id("fortress/fortress_interior_" + name);
            StructureTemplate part = templates.get(id).orElseThrow(() -> new IllegalStateException("Missing fortress interior template: " + id));
            BlockPos at = world(origin(), local.getX(), local.getY(), local.getZ());
            StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(rotation.getRotated(facing))
                .setMirror(mirror).setBoundingBox(clip).addProcessor(BlockIgnoreProcessor.STRUCTURE_BLOCK);
            boolean partition = processor instanceof InteriorPartition;
            // Partition slices already have their final shapes; neighbor repair would
            // reconnect them to room decorations or unfinished adjacent slices.
            settings.setKnownShape(partition);
            if (processor != null) settings.addProcessor(processor);
            settings.addProcessor(palette);
            if (!part.getBoundingBox(settings, at).intersects(clip)) return;
            part.placeInWorld(level, at, at, settings, random, partition ? Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE : Block.UPDATE_CLIENTS);
        }

        private void placeInterior(InteriorPlan plan, WorldGenLevel level, BoundingBox clip, RandomSource random) {
            BlockPos origin = origin();
            InteriorRoute route = plan.route;
            InteriorLayout layout = route == null ? null : plan.layout(footprint);
            Set<Integer> active = route == null ? Set.of() : Set.copyOf(route.regions());
            List<InteriorRoom> selectedRooms = new ArrayList<>();
            int[][] owner = new int[footprint.width][footprint.depth];
            int index = 0;
            for (InteriorRoom room : plan.rooms) {
                index++;
                Set<BlockPos> columns = room.columns();
                BlockPos first = columns.iterator().next();
                if (layout != null && !active.contains(layout.region(first.getX(), first.getZ()))) continue;
                selectedRooms.add(room);
                for (BlockPos column : columns) owner[column.getX()][column.getZ()] = index;
            }
            Set<BlockPos> remaining = new LinkedHashSet<>();
            for (int x = 0; x < footprint.width; x++) for (int z = 0; z < footprint.depth; z++) {
                if (footprint.interior(x, z) && owner[x][z] == 0) remaining.add(world(origin, x, 0, z));
            }
            InteriorFill fill = new InteriorFill(remaining, origin.getY());
            // Approved narrow perimeter strips and hallways reuse the authored plain section's surfaces.
            for (int x = 0; x < footprint.width; x += 8) for (int z = 0; z < footprint.depth; z += 8)
                interiorPart("single_01", new BlockPos(x, 0, z), Rotation.NONE, level, clip, random, fill);
            for (InteriorRoom room : selectedRooms)
                interiorPart(room.name(), room.offset(), room.rotation(), room.mirror(), level, clip, random, null);

            if (route != null && layout != null) {
                BlockState divider = palette.apply(Blocks.NETHER_BRICKS.defaultBlockState());
                // Unvisited pockets are solid divider mass, never unreachable furnished/loot rooms.
                for (int x = 0; x < footprint.width; x++) for (int z = 0; z < footprint.depth; z++) {
                    int region = layout.region(x, z);
                    if (region == 0 || active.contains(region)) continue;
                    for (int y = 1; y <= 6; y++) {
                        BlockPos at = world(origin, x, y, z);
                        if (clip.isInside(at)) level.setBlock(at, divider, 2);
                    }
                }
                placeRouteWalls(layout, route, level, clip, random);
                return;
            }

            Map<BlockPos, WallBoundary> boundaries = new java.util.LinkedHashMap<>();
            for (int x = 1; x < footprint.width - 1; x++) for (int z = 1; z < footprint.depth - 1; z++) {
                if (owner[x][z] != 0 || !footprint.interior(x, z)) continue;
                int west = owner[x - 1][z], east = owner[x + 1][z], north = owner[x][z - 1], south = owner[x][z + 1];
                if (west != 0 && east != 0 && west != east)
                    boundaries.put(new BlockPos(x, 0, z), new WallBoundary(true, west, east));
                else if (north != 0 && south != 0 && north != south)
                    boundaries.put(new BlockPos(x, 0, z), new WallBoundary(false, north, south));
            }
            Set<BlockPos> visited = new LinkedHashSet<>(), wallColumns = new LinkedHashSet<>(boundaries.keySet());
            Set<BlockPos> openingColumns = new LinkedHashSet<>();
            List<InteriorWall> walls = new ArrayList<>();
            for (Map.Entry<BlockPos, WallBoundary> entry : boundaries.entrySet()) {
                BlockPos start = entry.getKey();
                if (!visited.add(start)) continue;
                WallBoundary edge = entry.getValue();
                Direction direction = edge.alongZ() ? Direction.SOUTH : Direction.EAST;
                int length = 1;
                while (edge.equals(boundaries.get(start.relative(direction, length)))) {
                    visited.add(start.relative(direction, length)); length++;
                }
                walls.add(new InteriorWall(start.getX(), start.getZ(), length, edge.alongZ(), "wall_regular"));
                // Every neighboring pair gets a two-wide, four-high passage; walls cannot isolate a room.
                if (length >= 6) {
                    openingColumns.add(start.relative(direction, (length - 2) / 2));
                    openingColumns.add(start.relative(direction, (length - 2) / 2 + 1));
                }
            }
            // Join the one-block grid intersections without introducing posts inside a room.
            for (int x = 1; x < footprint.width - 1; x++) for (int z = 1; z < footprint.depth - 1; z++) {
                BlockPos at = new BlockPos(x, 0, z);
                if (owner[x][z] != 0 || !footprint.interior(x, z) || boundaries.containsKey(at)) continue;
                int neighbors = 0;
                for (Direction direction : Direction.Plane.HORIZONTAL) if (boundaries.containsKey(at.relative(direction))) neighbors++;
                if (neighbors >= 2) wallColumns.add(at);
            }
            Set<BlockPos> posts = new LinkedHashSet<>(), openings = new LinkedHashSet<>();
            wallColumns.removeAll(openingColumns);
            for (BlockPos column : wallColumns) {
                boolean north = wallColumns.contains(column.north()), south = wallColumns.contains(column.south());
                boolean west = wallColumns.contains(column.west()), east = wallColumns.contains(column.east());
                boolean straight = north && south && !west && !east || west && east && !north && !south;
                if (!straight || !boundaries.containsKey(column)) posts.add(world(origin, column.getX(), 0, column.getZ()));
            }
            for (BlockPos column : openingColumns) openings.add(world(origin, column.getX(), 0, column.getZ()));
            InteriorPartition partition = new InteriorPartition(openings, posts, origin.getY());
            // Walls are placed last and may override room blocks where their end/corner joins require it.
            for (InteriorWall wall : walls) {
                for (int offset = 0; offset < wall.length(); offset += 8) {
                    int length = Math.min(8, wall.length() - offset);
                    int x = wall.x() + (wall.alongZ() ? 0 : offset), z = wall.z() + (wall.alongZ() ? offset : 0);
                    BoundingBox bounds = BoundingBox.fromCorners(world(origin, x, 0, z),
                        world(origin, x + (wall.alongZ() ? 0 : length - 1), 7, z + (wall.alongZ() ? length - 1 : 0)));
                    if (!bounds.intersects(clip)) continue;
                    BoundingBox intersection = new BoundingBox(Math.max(bounds.minX(), clip.minX()), Math.max(bounds.minY(), clip.minY()),
                        Math.max(bounds.minZ(), clip.minZ()), Math.min(bounds.maxX(), clip.maxX()), Math.min(bounds.maxY(), clip.maxY()), Math.min(bounds.maxZ(), clip.maxZ()));
                    interiorPart(wall.template(), new BlockPos(x, 0, z), wall.alongZ() ? Rotation.CLOCKWISE_90 : Rotation.NONE,
                        level, intersection, random, partition);
                }
            }
            for (BlockPos column : wallColumns) {
                BlockPos at = world(origin, column.getX(), 0, column.getZ());
                if (posts.contains(at)) interiorPart("wall_edge", column, Rotation.NONE, level, clip, random, null);
            }
        }

        private void placeRouteWalls(InteriorLayout layout, InteriorRoute route,
                WorldGenLevel level, BoundingBox clip, RandomSource random) {
            BlockPos origin = origin();
            Set<BlockPos> walls = new LinkedHashSet<>(layout.walls), openingColumns = new LinkedHashSet<>();
            for (int index : route.passages()) {
                InteriorPassage passage = layout.passages.get(index);
                if (route.hallways().contains(index)) {
                    for (int i = 0; i < passage.length(); i++) walls.remove(passage.start().relative(passage.direction(), i));
                } else {
                    int middle = (passage.length() - 2) / 2;
                    openingColumns.add(passage.start().relative(passage.direction(), middle));
                    openingColumns.add(passage.start().relative(passage.direction(), middle + 1));
                }
            }
            Set<BlockPos> solid = new LinkedHashSet<>(walls);
            solid.removeAll(openingColumns);
            Set<BlockPos> isolated = new LinkedHashSet<>();
            for (BlockPos at : solid) {
                boolean neighbor = false;
                for (Direction direction : Direction.Plane.HORIZONTAL) if (solid.contains(at.relative(direction))) neighbor = true;
                if (!neighbor && !layout.requiredWalls.contains(at)) isolated.add(at);
            }
            // Fully opened hallway joins do not leave isolated posts at a turn.
            walls.removeAll(isolated); solid.removeAll(isolated);
            Set<BlockPos> openings = new LinkedHashSet<>();
            for (BlockPos at : openingColumns) openings.add(world(origin, at.getX(), 0, at.getZ()));
            InteriorPartition processor = new InteriorPartition(openings, Set.of(), origin.getY());
            for (BlockPos at : walls) {
                // Door openings do not turn a continuous wall into ceiling-height end posts.
                boolean north = walls.contains(at.north()), south = walls.contains(at.south());
                boolean east = walls.contains(at.east()), west = walls.contains(at.west());
                boolean alongZ = Math.floorMod(at.getX() - 3, 9) == 0;
                boolean crossing = alongZ && Math.floorMod(at.getZ() - 3, 9) == 0;
                boolean straight = north && south && !east && !west || east && west && !north && !south;
                boolean post = !openingColumns.contains(at) && (!straight || crossing);
                int offset = Math.floorMod((alongZ ? at.getZ() : at.getX()) - 4, 9);
                if (offset == 8) post = true;
                if (layout.requiredWalls.contains(at)) { post = false; offset = 0; }
                String name = post ? "wall_edge" : "wall_regular";
                BlockPos local = post ? at : at.relative(alongZ ? Direction.NORTH : Direction.WEST, offset);
                BlockPos bottom = world(origin, at.getX(), 0, at.getZ());
                if (!clip.isInside(bottom)) continue;
                BoundingBox columnClip = new BoundingBox(bottom.getX(), Math.max(bottom.getY(), clip.minY()), bottom.getZ(),
                    bottom.getX(), Math.min(bottom.getY() + 7, clip.maxY()), bottom.getZ());
                interiorPart(name, local, !post && alongZ ? Rotation.CLOCKWISE_90 : Rotation.NONE,
                    level, columnClip, random, processor);
            }
        }

        private BlockPos world(BlockPos origin, int x, int y, int z) {
            return origin.offset(rotate(new BlockPos(x, y, z), rotation));
        }

        private synchronized void resolveFoundation(WorldGenLevel level, ChunkGenerator generator, BlockPos origin) {
            if (foundationY != Integer.MIN_VALUE) return;
            // Old saved halls also get one deterministic plane without querying
            // distant live chunks or observing already-placed neighboring supports.
            FoundationDensity density = new FoundationDensity(generator);
            foundationY = foundationBottom(footprint, origin, rotation, level.getMinY(), at -> density.column(
                () -> generator.getBaseColumn(at.getX(), at.getZ(), level, level.getLevel().getChunkSource().randomState())));
        }

        private void fillFoundation(WorldGenLevel level, BlockState state, int x, int top, int z, BoundingBox clip) {
            if (x < clip.minX() || x > clip.maxX() || z < clip.minZ() || z > clip.maxZ()) return;
            state = palette.apply(state);
            BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
            for (int y = Math.min(top, clip.maxY()); y >= Math.max(foundationY, clip.minY()); y--) {
                at.set(x, y, z);
                placePreservingLava(level, at, state);
            }
        }

        private void facade(WorldGenLevel level, BoundingBox chunkBB, RandomSource random, BlockPos origin,
                HallFace face, HallShell terrain) {
            int offset = 0;
            if (face.startOuter()) {
                facadeRun(level, chunkBB, random, origin, face, terrain, "start", 0, 6);
                offset = 6;
            }
            int end = face.length() - (face.endOuter() ? 6 : 0);
            while (offset < end) {
                int length = Math.min(30, end - offset);
                facadeRun(level, chunkBB, random, origin, face, terrain, "repeat", offset, length);
                offset += length;
            }
            if (face.endOuter()) facadeRun(level, chunkBB, random, origin, face, terrain, "end", end, 6);
        }

        private void facadeRun(WorldGenLevel level, BoundingBox chunkBB, RandomSource random, BlockPos origin,
                HallFace face, HallShell terrain, String part, int offset, int length) {
            BlockPos local = new BlockPos(face.x(), 0, face.z()).offset(rotate(new BlockPos(offset, 0, 0), face.facing()));
            Rotation combined = rotation.getRotated(face.facing());
            BlockPos at = world(origin, local.getX(), -4, local.getZ());
            BlockPos far = at.offset(rotate(new BlockPos(length - 1, 0, 3), combined));
            int left = Math.max(chunkBB.minX(), Math.min(at.getX(), far.getX()));
            int right = Math.min(chunkBB.maxX(), Math.max(at.getX(), far.getX()));
            int north = Math.max(chunkBB.minZ(), Math.min(at.getZ(), far.getZ()));
            int south = Math.min(chunkBB.maxZ(), Math.max(at.getZ(), far.getZ()));
            if (left > right || north > south) return;
            BoundingBox clip = new BoundingBox(left, chunkBB.minY(), north, right, chunkBB.maxY(), south);
            StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(combined).setBoundingBox(clip)
                .addProcessor(BlockIgnoreProcessor.STRUCTURE_AND_AIR).addProcessor(terrain).addProcessor(palette);
            template("wall_" + part).placeInWorld(level, at, at, settings, random, 2);
            BlockPos base = at.below();
            for (Block material : new Block[] {Blocks.NETHER_BRICKS, Blocks.NETHER_BRICK_WALL}) {
                for (StructureTemplate.StructureBlockInfo column : template("base_" + part).filterBlocks(base, settings, material)) {
                    if (terrain.allows(column.pos()))
                        fillFoundation(level, column.state(), column.pos().getX(), base.getY(), column.pos().getZ(), clip);
                }
            }
        }

        private static BlockPos cornerOrigin(HallCorner corner) {
            BlockPos offset = rotate(new BlockPos(-4, 0, 0), corner.rotation());
            // Rotating cell indices about a grid vertex needs a one-cell correction.
            int dx = corner.rotation() == Rotation.CLOCKWISE_90 || corner.rotation() == Rotation.CLOCKWISE_180 ? -1 : 0;
            int dz = corner.rotation() == Rotation.CLOCKWISE_180 || corner.rotation() == Rotation.COUNTERCLOCKWISE_90 ? -1 : 0;
            return offset.offset(corner.x() + dx, 0, corner.z() + dz);
        }

        private void corner(WorldGenLevel level, BoundingBox chunkBB, RandomSource random, BlockPos origin,
                HallCorner corner, HallShell processor) {
            BlockPos local = cornerOrigin(corner);
            BlockPos at = world(origin, local.getX(), -4, local.getZ());
            Rotation combined = rotation.getRotated(corner.rotation());
            StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(combined).setBoundingBox(chunkBB)
                .addProcessor(BlockIgnoreProcessor.STRUCTURE_AND_AIR).addProcessor(processor).addProcessor(palette);
            template("inner_corner").placeInWorld(level, at, at, settings, random, 2);
            BlockPos base = at.below();
            StructurePlaceSettings unbounded = new StructurePlaceSettings().setRotation(combined);
            for (Block material : new Block[] {Blocks.NETHER_BRICKS, Blocks.NETHER_BRICK_WALL}) {
                for (StructureTemplate.StructureBlockInfo column : template("inner_base").filterBlocks(base, unbounded, material)) {
                    if (processor.allows(column.pos()))
                        fillFoundation(level, column.state(), column.pos().getX(), base.getY(), column.pos().getZ(), chunkBB);
                }
            }
        }

        @Override
        public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator,
                RandomSource random, BoundingBox chunkBB, ChunkPos chunkPos, BlockPos referencePos) {
            BlockPos origin = origin();
            resolveFoundation(level, generator, origin);
            BlockState bricks = palette.apply(Blocks.NETHER_BRICKS.defaultBlockState());
            Set<BlockPos> clearance = new LinkedHashSet<>();
            Set<BlockPos> interiorColumns = new LinkedHashSet<>();
            Set<BlockPos> cornerColumns = new LinkedHashSet<>();
            for (HallCorner corner : footprint.corners) {
                BlockPos local = cornerOrigin(corner);
                for (int x = 0; x < 4; x++) for (int z = 0; z < 4; z++) {
                    BlockPos column = local.offset(rotate(new BlockPos(x, 0, z), corner.rotation()));
                    cornerColumns.add(world(origin, column.getX(), 0, column.getZ()));
                }
            }
            for (int x = 0; x < footprint.width; x++) for (int z = 0; z < footprint.depth; z++) {
                BlockPos probe = world(origin, x, 0, z);
                boolean interior = footprint.interior(x, z);
                if (interior) interiorColumns.add(probe);
                if (!chunkBB.isInside(probe)) continue;
                boolean doorway = x >= width / 2 - 2 && x <= width / 2 + 1
                    && (z <= 3 || throughEntrance && z >= depth - 4 && z < depth);
                for (int y = 1; y <= 6; y++) {
                    if (interior || doorway && y <= 5) clearance.add(probe.above(y));
                }
                if (footprint.core(x, z)) {
                    if (!interior || this.interior == null) {
                        level.setBlock(probe, bricks, 2);
                        level.setBlock(probe.above(7), bricks, 2);
                    }
                    for (int y = 1; y <= 4; y++) level.setBlock(probe.below(y), bricks, 2);
                    fillFoundation(level, bricks, probe.getX(), probe.getY() - 5, probe.getZ(), chunkBB);
                }
            }
            for (int index = 0; index < footprint.faces.size(); index++) {
                HallFace face = footprint.faces.get(index);
                Set<BlockPos> columns = new LinkedHashSet<>();
                for (int along = 0; along < face.length(); along++) {
                    if (!footprint.ownsEdge(index, along)) continue;
                    for (int inward = 0; inward < 4; inward++) {
                        BlockPos local = new BlockPos(face.x(), 0, face.z())
                            .offset(rotate(new BlockPos(along, 0, inward), face.facing()));
                        columns.add(world(origin, local.getX(), 0, local.getZ()));
                    }
                }
                if (columns.isEmpty()) continue;
                HallShell shell = new HallShell(interiorColumns, cornerColumns, origin.getY(), false, columns);
                facade(level, chunkBB, random, origin, face, shell);
            }
            HallShell cornerShell = new HallShell(interiorColumns, cornerColumns, origin.getY(), true, null);
            for (HallCorner corner : footprint.corners) corner(level, chunkBB, random, origin, corner, cornerShell);
            // Carve the union once after shells, opening seams into connected halls.
            for (BlockPos at : clearance) level.setBlock(at, Blocks.AIR.defaultBlockState(), 2);
            InteriorPlan plan = interior;
            if (plan != null) placeInterior(plan, level, chunkBB, random);
            InteriorAir terrain = new InteriorAir(clearance);
            BlockPos entry = world(origin, width / 2 - 3, 0, 0);
            StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(rotation)
                .setBoundingBox(chunkBB).addProcessor(BlockIgnoreProcessor.STRUCTURE_BLOCK).addProcessor(terrain).addProcessor(palette);
            template("entrance").placeInWorld(level, entry, entry, settings, random, 2);
            if (throughEntrance) {
                BlockPos rear = world(origin, width / 2 + 2, 0, depth - 1);
                settings.setRotation(rotation.getRotated(Rotation.CLOCKWISE_180));
                template("entrance").placeInWorld(level, rear, rear, settings, random, 2);
            }
        }
    }

    /** Legacy saved modular buildings retain their original piece loader.
     * A seeded cell plan is kept as one piece so overlapping shell/interior
     * templates always run in the same order in every intersecting chunk. */
    private static final class BuildingPiece extends StructurePiece {
        private final StructureTemplateManager templates;
        private final Rotation buildingRotation;
        private final long seed;
        private final BuildingPlan plan;
        private volatile int foundationY = Integer.MIN_VALUE;

        private BuildingPiece(StructureTemplateManager templates, Rotation rotation, long seed, BuildingPlan plan) {
            super(BUILDING_TYPE, 0, new BoundingBox(0, 0, 0, 0, 0, 0));
            this.templates = templates;
            this.buildingRotation = rotation;
            this.seed = seed;
            this.plan = plan;
            setOrientation(null);
            for (BuildingPart part : plan.parts) template(part.name());
            template("roof_fill");
        }

        BuildingPiece(StructureTemplateManager templates, CompoundTag tag) {
            this(templates, tag.read("BuildingRotation", Rotation.CODEC).orElseThrow(), tag.getLongOr("PlanSeed", 0),
                new BuildingPlan(tag.getLongOr("PlanSeed", 0)));
            boundingBox = tag.read("BB", BoundingBox.CODEC).orElseThrow();
            foundationY = tag.getIntOr("FoundationY", Integer.MIN_VALUE);
        }

        private static BoundingBox bounds(BlockPos origin, Rotation rotation, int width, int depth, int top, int margin) {
            BlockPos first = origin.offset(rotate(new BlockPos(-1 - margin, -4, -1 - margin), rotation));
            BlockPos last = origin.offset(rotate(new BlockPos(width + margin, top, depth + margin), rotation));
            return BoundingBox.fromCorners(first, last);
        }

        private BlockPos origin() {
            BoundingBox relative = bounds(BlockPos.ZERO, buildingRotation, plan.width * 8, plan.depth * 8,
                plan.floors * 4 + 3, 1);
            // Deriving this from BB also follows vanilla's final height adjustment
            // without a second mutable origin or a deprecated move override.
            return new BlockPos(boundingBox.minX() - relative.minX(), boundingBox.minY() - relative.minY(),
                boundingBox.minZ() - relative.minZ());
        }

        private StructureTemplate template(String name) {
            Identifier id = Naturality.id("fortress/fortress_" + name);
            return templates.get(id).orElseThrow(() -> new IllegalStateException("Missing fortress building template: " + id));
        }

        private BlockPos world(BlockPos origin, BlockPos local) {
            return origin.offset(rotate(local, buildingRotation));
        }

        @Override
        protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
            tag.store("BuildingRotation", Rotation.CODEC, buildingRotation);
            tag.putLong("PlanSeed", seed);
            if (foundationY != Integer.MIN_VALUE) tag.putInt("FoundationY", foundationY);
        }

        private void place(BuildingPart part, BlockPos origin, int dy, WorldGenLevel level,
                BoundingBox clip, RandomSource random, boolean ignoreAir) {
            StructurePlaceSettings settings = new StructurePlaceSettings()
                .setRotation(buildingRotation.getRotated(part.rotation())).setBoundingBox(clip)
                .addProcessor(ignoreAir ? BlockIgnoreProcessor.STRUCTURE_AND_AIR : BlockIgnoreProcessor.STRUCTURE_BLOCK);
            BlockPos at = world(origin, part.position().offset(0, dy, 0));
            template(part.name()).placeInWorld(level, at, at, settings, random, 2);
        }

        @Override
        public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator,
                RandomSource random, BoundingBox chunkBB, ChunkPos chunkPos, BlockPos referencePos) {
            BlockPos origin = origin();
            Set<BlockPos> supports = new LinkedHashSet<>();
            for (BuildingPart part : plan.parts) {
                if (!part.foundation()) continue;
                StructurePlaceSettings settings = new StructurePlaceSettings()
                    .setRotation(buildingRotation.getRotated(part.rotation()));
                for (StructureTemplate.StructureBlockInfo block : template(part.name()).filterBlocks(
                        world(origin, part.position()), settings, Blocks.NETHER_BRICKS)) {
                    if (block.pos().getY() == origin.getY() - 4) supports.add(block.pos());
                }
            }
            Set<BlockPos> expanded = new LinkedHashSet<>();
            for (BlockPos support : supports) {
                for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
                    expanded.add(support.offset(dx, 0, dz));
                }
            }
            int contact = foundationHeight(level, expanded);
            // Interiors precede shells; floors follow both so the top-row air
            // in a doorway cannot cut a hole in the next storey's floor.
            for (BuildingPart part : plan.parts) {
                if (part.stage() == 0) place(part, origin, 0, level, chunkBB, random, false);
            }
            for (BlockPos column : plan.floorColumns) {
                for (int y = plan.floors * 4; y <= plan.floors * 4 + 3; y++) {
                    BlockPos at = world(origin, column.offset(0, y, 0));
                    if (chunkBB.isInside(at)) level.setBlock(at, Blocks.AIR.defaultBlockState(), 2);
                }
            }
            for (BuildingPart part : plan.parts) {
                if (part.stage() == 1) place(part, origin, 0, level, chunkBB, random, false);
            }
            StructureTemplate floor = template("roof_fill");
            if (floor.getSize().getX() != 8 || floor.getSize().getY() != 1 || floor.getSize().getZ() != 8
                    || floor.filterBlocks(BlockPos.ZERO, new StructurePlaceSettings(), Blocks.NETHER_BRICKS).size() != 64) {
                throw new IllegalStateException("Fortress floor fill must remain a solid 8x1x8 nether-brick tile");
            }
            // The supplied solid tile also covers the one-block shell seams.
            for (BlockPos column : plan.floorColumns) {
                for (int storey = 0; storey <= plan.floors; storey++) {
                    BlockPos at = world(origin, column.offset(0, storey * 4 - 1, 0));
                    if (chunkBB.isInside(at)) level.setBlock(at, Blocks.NETHER_BRICKS.defaultBlockState(), 2);
                }
            }
            // The stair template has an empty upper half. Preserve the upper
            // landing, opening only the two-wide ascending run through its floor.
            for (int storey = 0; storey < plan.floors - 1; storey++) {
                int stairZ = (storey + 1) * 8;
                for (int x = 8; x <= 9; x++) for (int z = stairZ; z <= stairZ + 3; z++) {
                    BlockPos at = world(origin, new BlockPos(x, storey * 4 + 3, z));
                    if (chunkBB.isInside(at)) level.setBlock(at, Blocks.AIR.defaultBlockState(), 2);
                }
            }
            for (BuildingPart part : plan.parts) {
                if (part.stage() == 2) place(part, origin, 0, level, chunkBB, random, true);
                if (!part.foundation()) continue;
                for (int dy = -4; origin.getY() + dy > contact; dy -= 4) {
                    int bottom = Math.max(contact + 1, chunkBB.minY());
                    BoundingBox clipped = new BoundingBox(chunkBB.minX(), bottom, chunkBB.minZ(),
                        chunkBB.maxX(), chunkBB.maxY(), chunkBB.maxZ());
                    place(part, origin, dy, level, clipped, random, false);
                }
            }
            for (BlockPos column : expanded) {
                fillColumnDown(level, Blocks.NETHER_BRICKS.defaultBlockState(), column.getX(), contact, column.getZ(), chunkBB);
            }
        }

        private synchronized int foundationHeight(WorldGenLevel level, Set<BlockPos> columns) {
            if (foundationY != Integer.MIN_VALUE) return foundationY;
            int highest = level.getMinY();
            for (BlockPos column : columns) {
                BlockPos.MutableBlockPos at = column.below().mutable();
                int top = at.getY();
                while (at.getY() > level.getMinY() + 1) {
                    BlockState state = level.getBlockState(at);
                    boolean liquid = state.getBlock() instanceof LiquidBlock;
                    if (liquid || !isReplaceableByStructures(state)) {
                        highest = Math.max(highest, Math.min(top, at.getY() + (liquid ? 0 : 1)));
                        break;
                    }
                    at.move(Direction.DOWN);
                }
            }
            foundationY = highest;
            return highest;
        }
    }

    private record BuildingPart(String name, BlockPos position, Rotation rotation, int stage, boolean foundation) {}

    /** Grid assignment precedes templates, like the mansion's floor-plan pass. */
    private static final class BuildingPlan {
        final int width;
        final int depth;
        final int floors;
        final boolean cutCorner;
        final List<BuildingPart> parts = new ArrayList<>();
        final Set<BlockPos> floorColumns = new LinkedHashSet<>();

        BuildingPlan(long seed) {
            RandomSource random = RandomSource.create(seed);
            width = 4 + random.nextInt(2);
            depth = 3 + random.nextInt(2);
            floors = 2 + random.nextInt(2);
            cutCorner = random.nextBoolean();
            for (int z = 0; z < depth; z++) for (int x = 0; x < width; x++) {
                if (!occupied(x, z)) continue;
                add("foundation_fill", x * 8, -4, z * 8, Rotation.NONE, 0, false);
                for (int dx = -1; dx <= 8; dx++) for (int dz = -1; dz <= 8; dz++) {
                    floorColumns.add(new BlockPos(x * 8 + dx, 0, z * 8 + dz));
                }
            }
            for (int floor = 0; floor < floors; floor++) rooms(floor, random);
            shell("foundation", -4, 3, random);
            shell("bottom", 0, 1, random);
            for (int floor = 1; floor < floors; floor++) shell("middle", floor * 4, 1, random);
            shell("roof", floors * 4, 1, random);
            for (int floor = 0; floor < floors - 1; floor++) {
                // Successive flights occupy different corridor cells: stacking
                // this authored stair directly would obstruct the lower headroom.
                add("stairs", 8, floor * 4, (floor + 1) * 8, Rotation.NONE, 2, false);
            }
        }

        private boolean occupied(int x, int z) {
            return x >= 0 && x < width && z >= 0 && z < depth
                && !(cutCorner && x == width - 1 && z == depth - 1);
        }

        private void add(String name, int x, int y, int z, Rotation rotation, int stage, boolean foundation) {
            parts.add(new BuildingPart(name, new BlockPos(x, y, z), rotation, stage, foundation));
        }

        private void rooms(int floor, RandomSource random) {
            int[][] rooms = new int[width][depth];
            int next = 1;
            // A continuous central corridor reaches the entrance and every stair
            // landing; room groups on either side may cover one, two or four cells.
            for (int z = 0; z < depth; z++) {
                rooms[1][z] = next++;
                add("corridor", 8, floor * 4, z * 8, Rotation.NONE, 0, false);
            }
            for (int z = 0; z < depth; z++) for (int x = 0; x < width; x++) {
                if (!occupied(x, z) || rooms[x][z] != 0) continue;
                int sx = 1;
                int sz = 1;
                if (free(rooms, x + 1, z) && random.nextBoolean()) sx = 2;
                if (free(rooms, x, z + 1) && (sx == 1 || free(rooms, x + 1, z + 1)) && random.nextBoolean()) sz = 2;
                for (int dx = 0; dx < sx; dx++) for (int dz = 0; dz < sz; dz++) rooms[x + dx][z + dz] = next;
                next++;
                String name = sx == 2 && sz == 2 ? "room_large" : sx * sz == 2 ? "room_double"
                    : random.nextBoolean() ? "room_01" : "room_02";
                Rotation rotation = sz == 2 && sx == 1 ? Rotation.CLOCKWISE_90
                    : sx == 2 && sz == 1 ? Rotation.NONE : Rotation.getRandom(random);
                // Normalize rotated templates into their assigned cell rectangle.
                int templateWidth = sx * sz == 2 ? 16 : sx * 8;
                int templateDepth = sx * sz == 2 ? 8 : sz * 8;
                BlockPos far = rotate(new BlockPos(templateWidth - 1, 0, templateDepth - 1), rotation);
                add(name, x * 8 - Math.min(0, far.getX()), floor * 4, z * 8 - Math.min(0, far.getZ()), rotation, 0, false);
            }
            List<RoomEdge> edges = new ArrayList<>();
            for (int z = 0; z < depth; z++) for (int x = 0; x < width; x++) {
                if (!occupied(x, z)) continue;
                if (occupied(x + 1, z) && rooms[x][z] != rooms[x + 1][z]) {
                    edges.add(new RoomEdge(x, z, true, rooms[x][z], rooms[x + 1][z]));
                }
                if (occupied(x, z + 1) && rooms[x][z] != rooms[x][z + 1]) {
                    edges.add(new RoomEdge(x, z, false, rooms[x][z], rooms[x][z + 1]));
                }
            }
            shuffle(edges, random);
            int[] groups = new int[next];
            for (int index = 0; index < next; index++) groups[index] = index;
            for (RoomEdge edge : edges) {
                int first = group(groups, edge.first());
                int second = group(groups, edge.second());
                // The west edge of a stair cell meets the risers/open stairwell,
                // not a level landing. Route those rooms through adjacent rooms.
                boolean stairSide = edge.east() && edge.x() == 0
                    && (floor < floors - 1 && edge.z() == floor + 1 || floor > 0 && edge.z() == floor);
                boolean doorway = !stairSide && (first != second || random.nextBoolean());
                // Keep the central corridor open even when its graph already has a loop.
                boolean corridor = !edge.east() && edge.x() == 1;
                if (doorway || corridor) groups[first] = second;
                if (corridor) continue;
                add(doorway ? "doorway" : "partition", edge.x() * 8 + (edge.east() ? 7 : 0), floor * 4,
                    edge.z() * 8 + (edge.east() ? 0 : 7), edge.east() ? Rotation.CLOCKWISE_90 : Rotation.NONE, 1, false);
            }
        }

        private boolean free(int[][] rooms, int x, int z) {
            return occupied(x, z) && rooms[x][z] == 0;
        }

        private static int group(int[] groups, int index) {
            while (groups[index] != index) index = groups[index];
            return index;
        }

        private void shell(String set, int y, int thickness, RandomSource random) {
            boolean foundation = set.equals("foundation");
            for (int z = 0; z < depth; z++) for (int x = 0; x < width; x++) {
                if (!occupied(x, z)) continue;
                for (Direction side : Direction.Plane.HORIZONTAL) {
                    if (occupied(x + side.getStepX(), z + side.getStepZ())) continue;
                    String name = set + "_wall";
                    if (set.equals("middle") && random.nextBoolean()) name += "_02";
                    if (set.equals("bottom") && x == 1 && z == 0 && side == Direction.NORTH) name = "bottom_entrance";
                    switch (side) {
                        case NORTH -> add(name, x * 8, y, z * 8 - 1, Rotation.NONE, 1, foundation);
                        case EAST -> add(name, x * 8 + 8, y, z * 8, Rotation.CLOCKWISE_90, 1, foundation);
                        case SOUTH -> add(name, x * 8 + 7, y, z * 8 + 8, Rotation.CLOCKWISE_180, 1, foundation);
                        case WEST -> add(name, x * 8 - 1, y, z * 8 + 7, Rotation.COUNTERCLOCKWISE_90, 1, foundation);
                        default -> throw new IllegalStateException("Vertical wall edge");
                    }
                }
            }
            for (int vz = 0; vz <= depth; vz++) for (int vx = 0; vx <= width; vx++) {
                boolean nw = occupied(vx - 1, vz - 1);
                boolean ne = occupied(vx, vz - 1);
                boolean se = occupied(vx, vz);
                boolean sw = occupied(vx - 1, vz);
                int count = (nw ? 1 : 0) + (ne ? 1 : 0) + (se ? 1 : 0) + (sw ? 1 : 0);
                if (count != 1 && count != 3) continue;
                boolean inner = count == 3;
                // Quadrant of the occupied cell for a convex corner, or the
                // missing cell for a concave corner. Foundation corners are 3x3.
                int quadrant = (inner ? !se : se) ? 0 : (inner ? !sw : sw) ? 1 : (inner ? !nw : nw) ? 2 : 3;
                Rotation rotation = Rotation.values()[(quadrant + (foundation ? inner ? 1 : 3 : 0)) % 4];
                int minX = vx * 8 + ((quadrant == 0 || quadrant == 3) ? -1 : 1 - thickness);
                int minZ = vz * 8 + ((quadrant == 0 || quadrant == 1) ? -1 : 1 - thickness);
                if (inner) {
                    minX = vx * 8 + ((quadrant == 0 || quadrant == 3) ? 1 - thickness : -1);
                    minZ = vz * 8 + ((quadrant == 0 || quadrant == 1) ? 1 - thickness : -1);
                }
                BlockPos far = rotate(new BlockPos(thickness - 1, 0, thickness - 1), rotation);
                add(set + (inner ? "_corner_inner" : "_corner_outer"), minX - Math.min(0, far.getX()), y,
                    minZ - Math.min(0, far.getZ()), rotation, 1, foundation);
            }
        }
    }

    private record RoomEdge(int x, int z, boolean east, int first, int second) {}

    public static final class BridgePiece extends TemplateStructurePiece {
        private Palette palette = Palette.NORMAL;
        private static final Block[] SUPPORT_MATERIALS = {Blocks.NETHER_BRICKS, Blocks.NETHER_BRICK_WALL};
        private static final int UNPLANNED_FOUNDATION = Integer.MIN_VALUE;
        private volatile int foundationY = UNPLANNED_FOUNDATION;

        private BridgePiece(StructureTemplateManager templates, Identifier templateId, BlockPos position, Rotation rotation) {
            super(PIECE_TYPE, 0, templates, templateId, templateId.toString(), settings(rotation), position);
            // Template settings handle rotation; fillColumnDown receives absolute coordinates.
            setOrientation(null);
            includeLedgeBounds();
        }

        private BridgePiece(StructureTemplateManager templates, CompoundTag tag) {
            super(PIECE_TYPE, tag, templates,
                _ -> settings(tag.read("Rot", Rotation.CODEC).orElseThrow()));
            setOrientation(null);
            foundationY = tag.getIntOr("FoundationY", UNPLANNED_FOUNDATION);
            palette = Palette.load(tag);
            includeLedgeBounds();
        }

        private String part() { return templateName.substring(templateName.lastIndexOf("fortress_bridge_") + 16); }

        private BridgeTemplate description(StructureTemplateManager templates) { return BridgeTemplate.load(templates, part()); }

        private List<Connection> ports(StructureTemplateManager templates) {
            return description(templates).connections().stream().map(port -> port.transform(templatePosition, getRotation())).toList();
        }

        private BoundingBox layoutBounds() {
            return template.getBoundingBox(placeSettings, templatePosition);
        }

        private void includeLedgeBounds() {
            BoundingBox bounds = layoutBounds();
            // Clearance can extend beyond the exported box, especially above
            // the final stair. Include it so neighboring chunks run this piece.
            boundingBox = new BoundingBox(bounds.minX() - 2, bounds.minY(), bounds.minZ() - 2,
                bounds.maxX() + 2, bounds.maxY() + 4, bounds.maxZ() + 2);
        }

        private static StructurePlaceSettings settings(Rotation rotation) {
            // Clearance and terrain protection are attached at placement time.
            return new StructurePlaceSettings().setRotation(rotation)
                .addProcessor(BlockIgnoreProcessor.STRUCTURE_BLOCK);
        }

        @Override
        protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
            super.addAdditionalSaveData(context, tag);
            palette.save(tag);
            tag.store("Rot", Rotation.CODEC, getRotation());
            int height = foundationY;
            if (height != UNPLANNED_FOUNDATION) tag.putInt("FoundationY", height);
        }

        @Override
        protected void handleDataMarker(String markerId, BlockPos position, ServerLevelAccessor level,
                RandomSource random, BoundingBox chunkBB) {
            // The supplied prototype has no data markers.
        }

        private Set<BlockPos> walkwayClearance() {
            StructurePlaceSettings local = settings(Rotation.NONE);
            Set<BlockPos> air = new LinkedHashSet<>();
            for (StructureTemplate.StructureBlockInfo block : template.filterBlocks(BlockPos.ZERO, local, Blocks.AIR)) {
                air.add(block.pos());
            }
            Set<BlockPos> clearance = new LinkedHashSet<>();
            for (Block material : new Block[] {Blocks.NETHER_BRICKS, Blocks.NETHER_BRICK_STAIRS}) {
                for (StructureTemplate.StructureBlockInfo block : template.filterBlocks(BlockPos.ZERO, local, material)) {
                    BlockPos at = block.pos();
                    int deck = DECK_Y;
                    if (templateName.endsWith("_gate")) deck = 2;
                    else if (templateName.endsWith("_stair_end") || templateName.endsWith("_stair_endstart")) {
                        deck = Math.max(0, at.getX() - 1);
                    } else if (templateName.endsWith("_stair_start") || templateName.endsWith("_stair_middle")) {
                        deck = at.getX() + 1;
                    }
                    // Seed from actual walking lanes, then widen the terrain cut
                    // beyond the railings and continue it across section seams.
                    if (at.getY() != deck || !air.contains(at.above())) continue;
                    for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
                        for (int dy = 1; dy <= 4; dy++) {
                            clearance.add(templatePosition.offset(rotate(at.offset(dx, dy, dz), getRotation())));
                        }
                    }
                }
            }
            return clearance;
        }

        private static boolean authoredMasonry(BlockState state) {
            // Preserve adjacent authored geometry in every fortress palette while clearing terrain.
            return state.is(Blocks.NETHER_BRICKS)
                || state.is(Blocks.NETHER_BRICK_STAIRS)
                || state.is(Blocks.NETHER_BRICK_SLAB)
                || state.is(Blocks.NETHER_BRICK_WALL)
                || state.is(Blocks.NETHER_BRICK_FENCE)
                || state.is(Blocks.CRACKED_NETHER_BRICKS)
                || state.is(Blocks.CHISELED_NETHER_BRICKS)
                || state.is(Blocks.RED_NETHER_BRICKS)
                || state.is(Blocks.RED_NETHER_BRICK_STAIRS)
                || state.is(Blocks.RED_NETHER_BRICK_SLAB)
                || state.is(Blocks.RED_NETHER_BRICK_WALL)
                || state.is(ContentRegistration.NaturalityBlocks.CRIMSON_NETHER_BRICK_FENCE)
                || state.is(ContentRegistration.NaturalityBlocks.CRACKED_CRIMSON_NETHER_BRICKS)
                || state.is(ContentRegistration.NaturalityBlocks.CHISELED_CRIMSON_NETHER_BRICKS)
                || state.is(ContentRegistration.NaturalityBlocks.WARPED_NETHER_BRICKS)
                || state.is(ContentRegistration.NaturalityBlocks.WARPED_NETHER_BRICK_STAIRS)
                || state.is(ContentRegistration.NaturalityBlocks.WARPED_NETHER_BRICK_SLAB)
                || state.is(ContentRegistration.NaturalityBlocks.WARPED_NETHER_BRICK_WALL)
                || state.is(ContentRegistration.NaturalityBlocks.WARPED_NETHER_BRICK_FENCE)
                || state.is(ContentRegistration.NaturalityBlocks.CRACKED_WARPED_NETHER_BRICKS)
                || state.is(ContentRegistration.NaturalityBlocks.CHISELED_WARPED_NETHER_BRICKS);
        }

        @Override
        public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator,
                RandomSource random, BoundingBox chunkBB, ChunkPos chunkPos, BlockPos referencePos) {
            StructurePlaceSettings unbounded = settings(getRotation());
            List<StructureTemplate.StructureBlockInfo> supports = new ArrayList<>();
            Set<BlockPos> footprint = new LinkedHashSet<>();
            boolean stairLanding = templateName.endsWith("_stair_end") || templateName.endsWith("_stair_endstart");
            BlockPos across = rotate(new BlockPos(0, 0, 1), getRotation());
            for (Block material : SUPPORT_MATERIALS) {
                for (StructureTemplate.StructureBlockInfo support : template.filterBlocks(templatePosition, unbounded, material)) {
                    // Only the authored bottom layer continues downward. Slabs and
                    // stairs higher in the template form the arch, not extra pillars.
                    if (support.pos().getY() != templatePosition.getY()) continue;
                    if (stairLanding) {
                        BlockPos offset = support.pos().subtract(templatePosition);
                        int localZ = offset.getX() * across.getX() + offset.getZ() * across.getZ();
                        // The landing is four blocks across; continue only its
                        // middle two columns. Keep the authored landing intact.
                        if (localZ == 0 || localZ == template.getSize().getZ() - 1) continue;
                    }
                    supports.add(support);
                    footprint.add(support.pos());
                }
            }
            Set<BlockPos> expanded = new LinkedHashSet<>();
            for (BlockPos support : footprint) {
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        expanded.add(support.offset(dx, 0, dz));
                    }
                }
            }
            // Capture a shared surface before any chunk places the pillar or its base.
            int height = resolveFoundationHeight(level, expanded);
            // These authored templates have no markers or entities. Use per-chunk settings
            // directly so TemplateStructurePiece does not shrink our ledge bounds mid-generation.
            Set<BlockPos> clearance = walkwayClearance();
            for (BlockPos at : clearance) {
                if (!chunkBB.isInside(at)) continue;
                BlockState existing = level.getBlockState(at);
                // Adjacent pieces may already have placed a railing, gate or
                // stair. Carve terrain without erasing their authored geometry.
                if (!existing.isAir() && !authoredMasonry(existing)) level.setBlock(at, Blocks.AIR.defaultBlockState(), 2);
            }
            StructurePlaceSettings placement = settings(getRotation()).setBoundingBox(chunkBB)
                .addProcessor(new InteriorAir(clearance)).addProcessor(palette);
            template.placeInWorld(level, templatePosition, referencePos, placement, random, 2);
            for (StructureTemplate.StructureBlockInfo support : supports) {
                BlockPos.MutableBlockPos cursor = support.pos().below().mutable();
                if (!chunkBB.isInside(cursor)) continue;
                while (cursor.getY() > height && cursor.getY() > level.getMinY() + 1
                        && isReplaceableByStructures(level.getBlockState(cursor))) {
                    placePreservingLava(level, cursor, palette.apply(support.state()));
                    cursor.move(Direction.DOWN);
                }
            }
            for (BlockPos column : expanded) {
                // Include the original shaft: walls become full blocks at the ledge
                // and remain full blocks throughout the widened base below it.
                fillColumnDown(level, palette.apply(Blocks.NETHER_BRICKS.defaultBlockState()),
                    column.getX(), height, column.getZ(), chunkBB);
            }
        }

        private synchronized int resolveFoundationHeight(WorldGenLevel level, Set<BlockPos> expanded) {
            if (foundationY != UNPLANNED_FOUNDATION) return foundationY;
            int highestContact = level.getMinY();
            for (BlockPos column : expanded) {
                BlockPos.MutableBlockPos cursor = column.below().mutable();
                int top = cursor.getY();
                while (cursor.getY() > level.getMinY() + 1) {
                    BlockState state = level.getBlockState(cursor);
                    boolean liquid = state.getBlock() instanceof LiquidBlock;
                    if (liquid || !isReplaceableByStructures(state)) {
                        // One flat cap above the highest dry contact, or flush with
                        // the highest liquid surface, shared by the entire footprint.
                        int contact = liquid ? cursor.getY() : cursor.getY() + 1;
                        highestContact = Math.max(highestContact, Math.min(top, contact));
                        break;
                    }
                    cursor.move(Direction.DOWN);
                }
            }
            foundationY = highestContact;
            return highestContact;
        }
    }
}
