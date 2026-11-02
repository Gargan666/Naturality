package naturality.worldgen.nether.lavalakes;

import com.mojang.serialization.MapCodec;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import java.util.Optional;
import naturality.Naturality;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Interval;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.CarvingMask;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.VerticalAnchor;
import net.minecraft.world.level.levelgen.densityfunction.*;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.pools.JigsawJunction;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/** Terraced lava and sealed basin beds, shared by chunk filling and base-column queries. */
@NullMarked
public record NetherLavaLakes(DensityFunction input) implements DensityFunction {
    private static final ThreadLocal<Layout> SURFACE_LAYOUT = new ThreadLocal<>();
    public static final MapCodec<NetherLavaLakes> CODEC = DensityFunction.CODEC.fieldOf("input")
        .xmap(NetherLavaLakes::new, NetherLavaLakes::input);

    @Override public DensitySampler compileSampler(CompileContext context) {
        // Keep the feature's stable seed identifier across generation versions.
        return new Sampler(input.compileSampler(context), context.createRandom(Naturality.id("nether_terrain")));
    }
    @Override public DensityFunction rewriteChildren(DfRewriteRule rule) {
        return new NetherLavaLakes(rule.rewrite(input));
    }
    @Override public Interval range() { return Interval.INFINITE; }
    @Override public int domainAxes() { return ALL_AXES; }
    @Override public MapCodec<NetherLavaLakes> codec() { return CODEC; }

    /** Anchor the assembled rigid bastion to a local basin, rather than a fixed global Y. */
    public static Optional<Structure.GenerationStub> anchorBastion(
            Structure.GenerationContext context,
            Structure.GenerationStub original) {
        var generator = (NoiseBasedChunkGenerator)context.chunkGenerator();
        var sampler = (Sampler)context.randomState().getSampler(new NetherLavaLakes(
            generator.generatorSettings().value().noiseRouter().finalDensity()));
        var density = sampler.bind(SamplerContext.EMPTY_UNCACHED);
        var builder = original.getPiecesBuilder();
        var pieces = builder.build().pieces();
        if (pieces.isEmpty()) return Optional.empty();
        var base = pieces.getFirst().getBoundingBox();
        var whole = builder.getBoundingBox();
        BlockPos best = null;
        int bestScore = Integer.MIN_VALUE;
        for (int dx : new int[] {0, -24, 24}) for (int dz : new int[] {0, -24, 24}) {
            int lake = sampler.layout.lavaLevel(base.getCenter().getX() + dx, base.getCenter().getZ() + dz);
            int bottom = lake - 4, dy = bottom - base.minY();
            if (whole.minY() + dy < context.heightAccessor().getMinY() + 5
                    || whole.maxY() + dy >= context.heightAccessor().getMaxY() - 5) continue;
            // Stay within vanilla's structure-reference radius of the owning chunk.
            int cx = context.chunkPos().getMiddleBlockX(), cz = context.chunkPos().getMiddleBlockZ();
            if (whole.minX() + dx < cx - 112 || whole.maxX() + dx > cx + 112
                    || whole.minZ() + dz < cz - 112 || whole.maxZ() + dz > cz + 112) continue;
            BlockPos position = original.position().offset(dx, dy, dz);
            if (!context.isValidBiome(new Structure.GenerationStub(position, _ -> {}))) continue;
            boolean uniformLake = true;
            for (int z = base.minZ() + dz; z <= base.maxZ() + dz && uniformLake; z++)
                for (int x = base.minX() + dx; x <= base.maxX() + dx; x++) {
                    if (sampler.layout.lavaLevel(x, z) != lake) { uniformLake = false; break; }
                }
            if (!uniformLake) continue;
            int lava = 0, samples = 0;
            for (int iz = 0; iz < 5; iz++) for (int ix = 0; ix < 5; ix++) {
                int x = base.minX() + dx + (base.getXSpan() - 1) * ix / 4;
                int z = base.minZ() + dz + (base.getZSpan() - 1) * iz / 4;
                // Below the uniform local surface, the aquifer supplies either lava
                // or basin terrain; it cannot leave an air gap under this base.
                if (density.sampleValue(x, lake - 1, z) <= 0 && density.sampleValue(x, lake + 4, z) <= 0) lava++;
                samples++;
            }
            // Favor broad lava sites. Keep a minority of supported, embedded land sites.
            if (lava * 2 < samples && Math.floorMod(context.chunkPos().pack() ^ context.seed(), 5) != 0) continue;
            int score = lava * 100 - Math.abs(dx) - Math.abs(dz);
            if (score > bestScore) { bestScore = score; best = new BlockPos(dx, dy, dz); }
        }
        if (best == null) return Optional.empty();
        int dx = best.getX(), dy = best.getY(), dz = best.getZ();
        for (var piece : pieces) {
            piece.move(dx, dy, dz);
            if (piece instanceof PoolElementStructurePiece pool) {
                pool.getJunctions().replaceAll(junction -> new JigsawJunction(
                    junction.getSourceX() + dx, junction.getSourceGroundY() + dy, junction.getSourceZ() + dz,
                    junction.getDeltaY(), junction.getDestProjection()));
            }
        }
        return Optional.of(new Structure.GenerationStub(
            original.position().offset(dx, dy, dz), target -> pieces.forEach(target::addPiece)));
    }

    private static Sampler sampler(NoiseChunk noise, DensityFunction input) {
        // The 26.3 compiler preserves this all-axis outer function's sampler. Reusing its
        // cache entry also makes fluid levels use precisely the geometry's world seed.
        return (Sampler) noise.cachingSamplers().get(new NetherLavaLakes(input)).sampler();
    }

    /** Material evaluators run synchronously on the generation worker, including carved floors. */
    public static void withSurfaceLevel(NoiseChunk noise, DensityFunction input, Runnable apply) {
        Layout previous = SURFACE_LAYOUT.get();
        SURFACE_LAYOUT.set(sampler(noise, input).layout);
        try {
            apply.run();
        } finally {
            if (previous == null) SURFACE_LAYOUT.remove();
            else SURFACE_LAYOUT.set(previous);
        }
    }

    public static int surfaceAnchor(int original, VerticalAnchor anchor, int x, int z) {
        Layout layout = SURFACE_LAYOUT.get();
        return layout != null && anchor instanceof VerticalAnchor.RelativeToSeaLevel relative
            ? layout.lavaLevel(x, z) + relative.offset() : original;
    }

    /** Check final blocks, after carvers and decoration; never load neighboring chunks here. */
    public static void updateExposedLava(ChunkAccess chunk) {
        var pos = new BlockPos.MutableBlockPos();
        var neighbor = new BlockPos.MutableBlockPos();
        int west = chunk.getPos().getMinBlockX(), north = chunk.getPos().getMinBlockZ();
        for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
            for (int y = chunk.getMinY(); y <= chunk.getMaxY(); y++) {
                pos.set(west + x, y, north + z);
                var fluid = chunk.getFluidState(pos);
                if (!fluid.is(FluidTags.LAVA) || !fluid.isSource()) continue;
                // The neighboring chunk's final terrain is not available at this stage.
                boolean exposed = x == 0 || x == 15 || z == 0 || z == 15;
                if (!exposed) for (Direction direction : Direction.values()) {
                    if (direction == Direction.UP) continue;
                    neighbor.setWithOffset(pos, direction);
                    BlockState state = chunk.getBlockState(neighbor);
                    var adjacent = state.getFluidState();
                    if (adjacent.is(FluidTags.LAVA) && adjacent.isSource()) continue;
                    if (!state.isFaceSturdy(chunk, neighbor, direction.getOpposite())) {
                        exposed = true;
                        break;
                    }
                }
                // Vanilla processes this queue after generation, when fluid physics can
                // safely see neighboring chunks. Interior source blocks need no tick.
                if (exposed) chunk.markPosForPostProcessing(pos);
            }
        }
    }

    public static Aquifer aquifer(Aquifer original, NoiseChunk noise, DensityFunction input) {
        DensitySampler.Bound densitySampler = noise.cachingSamplers().get(new NetherLavaLakes(input));
        Layout layout = ((Sampler) densitySampler.sampler()).layout;
        return new Aquifer() {
            private boolean update;

            @Override public @Nullable BlockState computeSubstance(int x, int y, int z, double density) {
                update = false;
                if (density > 0 || y < 5 || y >= 251) {
                    BlockState state = original.computeSubstance(x, y, z, density);
                    update = original.shouldScheduleFluidUpdate();
                    return state;
                }
                Column column = layout.column(x, z);
                if (y < column.lava) {
                    return Blocks.LAVA.defaultBlockState();
                }
                if (y < column.fallTop && hasSource(x, z, column.fallTop)
                        && clearFall(x, y, z, column.fallTop)) {
                    update = true;
                    return Blocks.LAVA.defaultBlockState().setValue(LiquidBlock.LEVEL, 8);
                }
                return Blocks.AIR.defaultBlockState();
            }

            private boolean clearFall(int x, int y, int z, int top) {
                // A pool above an overhang cannot feed a second, detached sheet below it.
                for (int above = y + 1; above < top; above++) {
                    if (densitySampler.sampleValue(x, above, z) > 0) return false;
                }
                return true;
            }

            private boolean hasSource(int x, int z, int level) {
                // Only expose a falling sheet where an upper pool actually feeds it.
                return sourceAt(x - 1, z, level) || sourceAt(x + 1, z, level)
                    || sourceAt(x, z - 1, level) || sourceAt(x, z + 1, level);
            }

            private boolean sourceAt(int x, int z, int level) {
                return layout.lavaLevel(x, z) == level
                    && densitySampler.sampleValue(x, level - 1, z) <= 0;
            }

            @Override public boolean shouldScheduleFluidUpdate() {
                return update;
            }
        };
    }

    public static CarvingMask.Filter protect(ChunkAccess chunk, NoiseChunk noise, DensityFunction input,
                                             CarvingMask.@Nullable Filter previous) {
        Layout layout = sampler(noise, input).layout;
        int west = chunk.getPos().getMinBlockX(), north = chunk.getPos().getMinBlockZ();
        return (x, y, z) -> {
            if (previous != null && !previous.test(x, y, z)) return false;
            Column column = layout.column(west + x, north + z);
            // Preserve the basin floor and the existing banks of narrow transitions.
            return y > Math.max(column.floor, column.ridge) + .5 && (y > 80 || layout.lake(west + x, north + z).shoreDistance >= 14);
        };
    }

    private record Lake(int level, double shoreDistance) {}
    private record Column(int lava, int fallTop, double floor, double ridge) {}

    /** Seeded height regions and their terrain boundaries; never reads or loads chunks. */
    public static final class Layout {
        private static final int SPACING = 192;
        private static final double ROTATION = .41, COS = Math.cos(ROTATION), SIN = Math.sin(ROTATION);
        private final SimplexNoise erosion, detail;
        private final DensitySampler terrain;
        private final long seed;
        private final ThreadLocal<Long2ObjectLinkedOpenHashMap<Column>> columns =
            ThreadLocal.withInitial(() -> new Long2ObjectLinkedOpenHashMap<>(1024));
        private final java.util.Map<Long, Gate> gates = new java.util.LinkedHashMap<>();
        private record Point(double u, double v) {}
        private record Edge(int across, int along, boolean uAxis, int negative, int positive) {}
        private record Gate(double x, double z, double nx, double nz, double halfWidth, boolean open) {
            double forward(double px, double pz) { return (px - x) * nx + (pz - z) * nz; }
            double sideways(double px, double pz) { return -(px - x) * nz + (pz - z) * nx; }
            double width(double forward) { return halfWidth + .48 * Math.abs(forward); }
        }

        public Layout(RandomSource random, DensitySampler terrain) {
            this.terrain = terrain;
            skipLegacyNoise(random);
            skipLegacyNoise(random);
            new SimplexNoise(random);
            erosion = new SimplexNoise(random);
            detail = new SimplexNoise(random);
            skipLegacyNoise(random);
            seed = random.nextLong();
        }

        private static void skipLegacyNoise(RandomSource random) {
            for (int offset = 0; offset < 3; offset++) random.nextDouble();
            for (int permutation = 0; permutation < 256; permutation++) random.nextInt(256 - permutation);
        }

        private static double ease(double t) {
            t = Math.clamp(t, 0, 1);
            return t * t * t * (t * (t * 6 - 15) + 10);
        }
        private static long key(int x, int z) { return (long)x << 32 ^ (z & 0xffffffffL); }
        private static long mixSeed(long value) {
            value = (value ^ value >>> 30) * 0xbf58476d1ce4e5b9L;
            value = (value ^ value >>> 27) * 0x94d049bb133111ebL;
            return value ^ value >>> 31;
        }

        private Point map(double x, double z) {
            double u = x * COS + z * SIN, v = z * COS - x * SIN;
            return new Point(u + 38 * erosion.get(u / 230, v / 230) + 9 * detail.get(u / 73, v / 73),
                v + 38 * erosion.get(u / 230 + 731, v / 230) + 9 * detail.get(u / 73 + 193, v / 73));
        }

        private Point unmap(double u, double v) {
            double x = u * COS - v * SIN, z = u * SIN + v * COS;
            for (int i = 0; i < 18; i++) {
                Point actual = map(x, z);
                double du = u - actual.u, dv = v - actual.v;
                x += .65 * (du * COS - dv * SIN);
                z += .65 * (du * SIN + dv * COS);
            }
            return new Point(x, z);
        }

        private int level(int gx, int gz) {
            return switch (Math.floorMod(mixSeed(seed ^ key(gx, gz)), 4)) {
                case 0 -> 52;
                case 1 -> 76;
                default -> 64;
            };
        }

        public int lavaLevel(int x, int z) {
            Point p = map(x, z);
            return level((int)Math.floor(p.u / SPACING), (int)Math.floor(p.v / SPACING));
        }

        private Edge edge(Point p, boolean uAxis) {
            int across = (int)Math.rint((uAxis ? p.u : p.v) / SPACING);
            int along = (int)Math.floor((uAxis ? p.v : p.u) / SPACING);
            int negative = uAxis ? level(across - 1, along) : level(along, across - 1);
            int positive = uAxis ? level(across, along) : level(along, across);
            return new Edge(across, along, uAxis, negative, positive);
        }

        private double distance(Point p, Edge edge) {
            return Math.abs((edge.uAxis ? p.u : p.v) - edge.across * SPACING);
        }

        private Lake lake(int x, int z) {
            Point p = map(x, z);
            double shore = 32;
            for (int axis = 0; axis < 2; axis++) {
                Edge edge = edge(p, axis == 0);
                if (edge.negative != edge.positive) shore = Math.min(shore, distance(p, edge));
            }
            return new Lake(level((int)Math.floor(p.u / SPACING), (int)Math.floor(p.v / SPACING)), shore);
        }

        private double ridge(int x, int z, Point p, Edge edge) {
            // Same-height lakes merge before any terrain is added.
            if (edge.negative == edge.positive) return 0;
            double width = 25 + 8 * erosion.get(x / 61.0, z / 61.0) + 4 * detail.get(x / 19.0, z / 19.0);
            double crown = Math.max(edge.negative, edge.positive) + 9
                + 5 * erosion.get(x / 39.0, z / 39.0) + 2 * detail.get(x / 11.0, z / 11.0);
            return 40 + (crown - 40) * ease(1 - distance(p, edge) / width);
        }

        private synchronized Gate gate(Edge edge) {
            long id = key(edge.across, edge.along) ^ (edge.uAxis ? 0 : Long.MIN_VALUE);
            Gate old = gates.get(id);
            if (old != null) return old;
            // A broader range, biased toward its larger end without removing small falls.
            long sizeSeed = mixSeed(seed ^ id);
            double halfWidth = 9 + Math.max(Math.floorMod(sizeSeed, 15), Math.floorMod(mixSeed(sizeSeed), 15));
            Gate made = new Gate(0, 0, 1, 0, halfWidth, false);
            if (Math.min(edge.negative, edge.positive) == 64 && Math.max(edge.negative, edge.positive) == 76) {
                ProofTerrain proof = new ProofTerrain();
                // Search more positions, preserving the original three first. Every
                // candidate still has to connect the same two substantial lakes.
                for (int candidate = 0; candidate < 20; candidate++) {
                    double offset = candidate < 3 ? (candidate - 1) * 34 : (candidate - 11) * 8;
                    double along = (edge.along + .5) * SPACING + offset;
                    Point center = edge.uAxis ? unmap(edge.across * SPACING, along) : unmap(along, edge.across * SPACING);
                    Point xp = map(center.u + 1, center.v), xm = map(center.u - 1, center.v);
                    Point zp = map(center.u, center.v + 1), zm = map(center.u, center.v - 1);
                    double nx = edge.uAxis ? xp.u - xm.u : xp.v - xm.v;
                    double nz = edge.uAxis ? zp.u - zm.u : zp.v - zm.v;
                    double sign = edge.positive < edge.negative ? 1 : -1;
                    double length = Math.hypot(nx, nz);
                    Gate trial = new Gate(center.u, center.v, sign * nx / length, sign * nz / length, halfWidth, true);
                    if (broadLakesAtBothEnds(trial, edge, proof)) { made = trial; break; }
                }
            }
            if (gates.size() >= 1024) gates.remove(gates.keySet().iterator().next());
            gates.put(id, made);
            return made;
        }

        /** Candidate footprints overlap heavily. Cache only the four predicates used by
         * the proof, and release all tiles when this edge's search completes. */
        private final class ProofTerrain {
            private final java.util.Map<Long, byte[]> tiles = new java.util.HashMap<>();

            int flags(int x, int z) {
                int tx = Math.floorDiv(x, 16), tz = Math.floorDiv(z, 16);
                byte[] tile = tiles.computeIfAbsent(key(tx, tz), _ -> sampleTile(tx, tz));
                return tile[Math.floorMod(z, 16) * 16 + Math.floorMod(x, 16)];
            }

            private byte[] sampleTile(int tx, int tz) {
                var context = SamplerContext.EMPTY_UNCACHED;
                var volume = new DensityVolume(16, 17, 16, tx * 16, 63, tz * 16);
                byte[] tile = new byte[256];
                try (var buffer = context.acquireBuffer(volume)) {
                    terrain.sampleVolume(context, buffer, volume);
                    for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
                        int flags = 15;
                        for (int y = 0; y < 17; y++) {
                            float density = buffer.get(volume.indexUnchecked(x, y, z));
                            if (density <= 0) continue;
                            flags &= ~4;
                            if (y < 4) flags &= ~1;
                            if (y >= 12 && y < 16) flags &= ~2;
                            if (y == 12) flags &= ~8;
                        }
                        tile[z * 16 + x] = (byte)flags;
                    }
                }
                return tile;
            }
        }

        private boolean broadLakesAtBothEnds(Gate gate, Edge owner, ProofTerrain proof) {
            final int size = 193;
            int west = (int)Math.floor(gate.x) - size / 2, north = (int)Math.floor(gate.z) - size / 2;
            byte[] levels = new byte[size * size];
            boolean[] sources = new boolean[levels.length], clear = new boolean[levels.length];
            float[] rocks = new float[levels.length];
            for (int iz = 0; iz < size; iz++) for (int ix = 0; ix < size; ix++) {
                int x = west + ix, z = north + iz, i = iz * size + ix;
                Point p = map(x, z);
                int level = level((int)Math.floor(p.u / SPACING), (int)Math.floor(p.v / SPACING));
                levels[i] = (byte)level;
                if (level == 52) continue;
                double rock = 0;
                for (int axis = 0; axis < 2; axis++) {
                    Edge edge = edge(p, axis == 0);
                    double shape = ridge(x, z, p, edge);
                    if (edge.equals(owner)) shape = openingRock(x, z, shape, gate);
                    rock = Math.max(rock, shape);
                }
                rocks[i] = (float)rock;
                int flags = proof.flags(x, z);
                sources[i] = rock + .5 < level - 1 && (flags & (level == 64 ? 1 : 2)) != 0;
                clear[i] = rock + .5 < 63 && (flags & 4) != 0;
            }
            int firstLow = -1, firstHigh = -1, width = 0;
            boolean[] mouths = new boolean[levels.length], upperMouths = new boolean[levels.length];
            for (int iz = 1; iz < size - 1; iz++) for (int ix = 1; ix < size - 1; ix++) {
                int i = iz * size + ix, x = west + ix, z = north + iz;
                if (levels[i] != 64 || Math.abs(gate.forward(x, z)) > 56
                        || Math.abs(gate.sideways(x, z)) > gate.width(gate.forward(x, z)) + 10) continue;
                if (rocks[i] + .5 >= 75 || (proof.flags(x, z) & 8) == 0) continue;
                for (int offset : new int[] {-1, 1, -size, size}) {
                    int next = i + offset;
                    if (levels[next] != 76 || !sources[next]) continue;
                    // Every part that can overflow must descend into source lava,
                    // not end on an unseen ledge above a dry cave.
                    if (!clear[i] || !sources[i]) return false;
                    if (!mouths[i]) { width++; mouths[i] = true; }
                    firstLow = i; firstHigh = next; upperMouths[next] = true;
                }
            }
            return width >= 16 && connectedLake(sources, levels, mouths, firstLow, 64, size)
                && connectedLake(sources, levels, upperMouths, firstHigh, 76, size);
        }

        private static boolean connectedLake(boolean[] sources, byte[] levels, boolean[] mouths,
                int start, int level, int size) {
            if (start < 0) return false;
            boolean[] reached = new boolean[sources.length];
            int[] queue = new int[sources.length];
            int head = 0, tail = 1;
            queue[0] = start; reached[start] = true;
            boolean broad = false;
            while (head < tail) {
                int i = queue[head++], x = i % size, z = i / size;
                if (!broad && x >= 6 && z >= 6 && x + 6 < size && z + 6 < size) {
                    broad = true;
                    for (int dz = -6; dz <= 6 && broad; dz++) for (int dx = -6; dx <= 6; dx++) {
                        int j = i + dz * size + dx;
                        if (!sources[j] || levels[j] != level) { broad = false; break; }
                    }
                }
                for (int direction = 0; direction < 4; direction++) {
                    int nx = x + (direction == 0 ? -1 : direction == 1 ? 1 : 0);
                    int nz = z + (direction == 2 ? -1 : direction == 3 ? 1 : 0);
                    if (nx < 0 || nz < 0 || nx >= size || nz >= size) continue;
                    int j = nz * size + nx;
                    if (!reached[j] && sources[j] && levels[j] == level) {
                        reached[j] = true; queue[tail++] = j;
                    }
                }
            }
            for (int i = 0; i < mouths.length; i++) if (mouths[i] && !reached[i]) return false;
            return broad && tail >= 625;
        }

        private static double openingRock(int x, int z, double rock, Gate gate) {
            if (!gate.open) return rock;
            double forward = gate.forward(x, z), side = Math.abs(gate.sideways(x, z));
            if (Math.abs(forward) > 56) return rock;
            double flank = ease((side - gate.halfWidth) / 25);
            if (forward > 0) rock = 40 + (rock - 40) * ease(1 - forward / (2 + 30 * flank));
            return 40 + (rock - 40) * ease((side - gate.width(forward)) / 7);
        }

        private double barrierTop(int x, int z) {
            Point p = map(x, z);
            double top = 0;
            for (int axis = 0; axis < 2; axis++) {
                Edge edge = edge(p, axis == 0);
                if (edge.negative == edge.positive || distance(p, edge) > 40) continue;
                double rock = ridge(x, z, p, edge);
                Gate gate = gate(edge);
                rock = openingRock(x, z, rock, gate);
                top = Math.max(top, rock);
            }
            return top;
        }

        private boolean approvedPassage(int x, int z) {
            Point p = map(x, z);
            for (int axis = 0; axis < 2; axis++) {
                Edge edge = edge(p, axis == 0);
                if (edge.negative == edge.positive || distance(p, edge) > 40) continue;
                Gate gate = gate(edge);
                if (gate.open && Math.abs(gate.forward(x, z)) <= 56
                        && Math.abs(gate.sideways(x, z)) <= gate.width(gate.forward(x, z)) + 8) return true;
            }
            return false;
        }

        private Column column(int x, int z) {
            long key = key(x, z);
            var cache = columns.get();
            Column old = cache.getAndMoveToFirst(key);
            if (old != null) return old;
            Lake lake = lake(x, z);
            int lava = lake.level, high = lava;
            for (Direction direction : Direction.Plane.HORIZONTAL)
                high = Math.max(high, lavaLevel(x + direction.getStepX(), z + direction.getStepZ()));
            double interior = ease(lake.shoreDistance / 20);
            double bed = lava - 2 - interior * (14 + erosion.get(x / 29.0, z / 29.0) * 2 + detail.get(x / 12.0, z / 12.0) * .8);
            if (lake.shoreDistance < 2.5 && !approvedPassage(x, z) && erosion.get(x / 16.0, z / 16.0) > .42) bed = lava - 1;
            Column made = new Column(lava, high, bed, barrierTop(x, z));
            if (cache.size() >= 2048) cache.removeLast();
            cache.putAndMoveToFirst(key, made);
            return made;
        }

        public float density(int x, int y, int z, float vanilla) { return density(column(x, z), y, vanilla); }

        private static float density(Column column, int y, float vanilla) {
            if (y <= 5) return vanilla;
            return (float)Math.max(vanilla, (Math.max(column.floor, column.ridge) + .5 - y) / 3);
        }
    }

    private static final class Sampler implements DensitySampler {
        private final DensitySampler vanilla;
        private final Layout layout;

        private Sampler(DensitySampler vanilla, RandomSource random) {
            this.vanilla = vanilla;
            layout = new Layout(random, vanilla);
        }

        @Override public float sampleValue(SamplerContext context, int x, int y, int z) {
            return layout.density(x, y, z, vanilla.sampleValue(context, x, y, z));
        }

        @Override public void sampleVolume(SamplerContext context, DensityBuffer output, DensityVolume volume) {
            vanilla.sampleVolume(context, output, volume);
            int index = 0;
            for (int z = 0; z < volume.sizeZ(); z++) for (int x = 0; x < volume.sizeX(); x++) {
                Column column = layout.column(volume.blockX(x), volume.blockZ(z));
                for (int y = 0; y < volume.sizeY(); y++) {
                    output.set(index, Layout.density(column, volume.blockY(y), output.get(index)));
                    index++;
                }
            }
        }
    }
}
