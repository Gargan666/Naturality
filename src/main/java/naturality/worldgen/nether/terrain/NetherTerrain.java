package naturality.worldgen.nether.terrain;

import com.mojang.serialization.MapCodec;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2BooleanOpenHashMap;
import java.util.ArrayList;
import java.util.List;
import naturality.Naturality;
import net.minecraft.util.Interval;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.CarvingMask;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.densityfunction.*;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/** Seeded cavern styles and terraced lava, shared by chunk filling and base-column queries. */
@NullMarked
public record NetherTerrain(DensityFunction input) implements DensityFunction {
    public static final MapCodec<NetherTerrain> CODEC = DensityFunction.CODEC.fieldOf("input")
        .xmap(NetherTerrain::new, NetherTerrain::input);

    @Override public DensitySampler compileSampler(CompileContext context) {
        return new Sampler(input.compileSampler(context), context.createRandom(Naturality.id("nether_terrain")));
    }
    @Override public DensityFunction rewriteChildren(DfRewriteRule rule) {
        return new NetherTerrain(rule.rewrite(input));
    }
    @Override public Interval range() { return Interval.INFINITE; }
    @Override public int domainAxes() { return ALL_AXES; }
    @Override public MapCodec<NetherTerrain> codec() { return CODEC; }

    private static Sampler sampler(NoiseChunk noise, DensityFunction input) {
        // The 26.3 compiler preserves this all-axis outer function's sampler. Reusing its
        // cache entry also makes fluid levels use precisely the geometry's world seed.
        return (Sampler) noise.cachingSamplers().get(new NetherTerrain(input)).sampler();
    }

    public static Aquifer aquifer(Aquifer original, NoiseChunk noise, DensityFunction input) {
        DensitySampler.Bound densitySampler = noise.cachingSamplers().get(new NetherTerrain(input));
        Layout layout = ((Sampler) densitySampler.sampler()).layout;
        return new Aquifer() {
            private boolean update;
            // Context-specific density must never go into the shared layout caches.
            private final Long2BooleanOpenHashMap connectedSources = new Long2BooleanOpenHashMap();

            @Override public @Nullable BlockState computeSubstance(int x, int y, int z, double density) {
                update = false;
                if (density > 0 || y < 5 || y >= 123) {
                    BlockState state = original.computeSubstance(x, y, z, density);
                    update = original.shouldScheduleFluidUpdate();
                    return state;
                }
                Column column = layout.column(x, z);
                if (y < column.lava) {
                    if (layout.lake(x, z).shoreDistance < 6 && !sourceAt(x, z, column.lava))
                        return Blocks.AIR.defaultBlockState();
                    return Blocks.LAVA.defaultBlockState();
                }
                if (y < column.fallTop && hasSource(x, z, column.fallTop)) {
                    update = true;
                    return Blocks.LAVA.defaultBlockState().setValue(LiquidBlock.LEVEL, 8);
                }
                return Blocks.AIR.defaultBlockState();
            }

            private boolean hasSource(int x, int z, int level) {
                // Only expose a falling sheet where an upper pool actually feeds it.
                return sourceAt(x - 1, z, level) || sourceAt(x + 1, z, level)
                    || sourceAt(x, z - 1, level) || sourceAt(x, z + 1, level);
            }

            private boolean sourceAt(int x, int z, int level) {
                if (layout.lavaLevel(x, z) != level) return false;
                long key = Layout.key(x, z);
                if (connectedSources.containsKey(key)) return connectedSources.get(key);
                boolean connected = layout.connectedToLake(x, z, level,
                    (px, pz) -> densitySampler.sampleValue(px, level - 1, pz) <= 0);
                if (connectedSources.size() >= 1024) connectedSources.clear();
                connectedSources.put(key, connected);
                return connected;
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
            // Keep basin beds sealed as well as preserving thin bridges and spirals.
            return y > column.floor + .5
                && !((column.bridges > .02 || column.islands > .02 || column.rivers > .02) && y > 7 && y < 119);
        };
    }

    private record Node(double x, double z, double radius, int top, boolean roof, boolean stairs,
                        double phase, boolean east, boolean south, double bottom, int tiers) {}
    private record Pillar(double dx, double dz, double roughness, Node node) {}
    private record Basin(double x, double z, double radius, double aspect, double angle, double core) {}
    private record Lake(int level, double shoreDistance) {}
    private record FlatIsland(double x, double z, double radius, double phase, double top, boolean present) {}
    private record Shelf(double edge, double top, double depth) {
        double density(int y) { return Math.min(edge, Math.min(top + .5 - y, y - (top - depth))) / 3; }
    }
    private record River(double distance, double width, double floor, double roof, double top) {
        double density(int y) {
            double tunnel = Math.max((distance - width) / 3,
                Math.max((floor + .5 - y) / 3, (y - roof) / 3));
            return Math.min((top - y) / 5, tunnel);
        }
    }
    private record Column(double bridges, double islands, double rivers, @Nullable River river, int lava, int fallTop,
                          double floor, double ceiling, List<Pillar> pillars,
                          List<Shelf> spans, List<Shelf> islets) {}

    /** Immutable seeded noise with bounded, worker-local caches of context-independent geometry. */
    public static final class Layout {
        private static double ease(double t) {
            t = Math.clamp(t, 0, 1);
            return t * t * t * (t * (t * 6 - 15) + 10);
        }
        private static double mix(double a, double b, double t) { return a + (b - a) * t; }
        private static double bounded(double density) { return Math.clamp(density, -1, 1); }
        private static long key(int x, int z) { return (long) x << 32 ^ (z & 0xffffffffL); }

        private static final int SPACING = 44;
        private static final double TAU = Math.PI * 2;
        private final SimplexNoise regions, patches, lakes, erosion, detail, elevation;
        private final SimplexNoise riverRegions, riverWarp, riverShape;
        private final long seed;
        private final ThreadLocal<Long2ObjectLinkedOpenHashMap<Column>> columns =
            ThreadLocal.withInitial(() -> new Long2ObjectLinkedOpenHashMap<>(1024));
        private final ThreadLocal<Long2ObjectLinkedOpenHashMap<Node>> nodes =
            ThreadLocal.withInitial(() -> new Long2ObjectLinkedOpenHashMap<>(256));
        private final ThreadLocal<Long2ObjectLinkedOpenHashMap<Basin>> basins =
            ThreadLocal.withInitial(() -> new Long2ObjectLinkedOpenHashMap<>(128));
        private final ThreadLocal<Long2ObjectLinkedOpenHashMap<Lake>> lakeColumns =
            ThreadLocal.withInitial(() -> new Long2ObjectLinkedOpenHashMap<>(2048));
        private final ThreadLocal<Long2ObjectLinkedOpenHashMap<FlatIsland>> flatIslands =
            ThreadLocal.withInitial(() -> new Long2ObjectLinkedOpenHashMap<>(256));

        public Layout(RandomSource random) {
            regions = new SimplexNoise(random);
            patches = new SimplexNoise(random);
            lakes = new SimplexNoise(random);
            erosion = new SimplexNoise(random);
            detail = new SimplexNoise(random);
            elevation = new SimplexNoise(random);
            seed = random.nextLong();
            riverRegions = new SimplexNoise(random);
            riverWarp = new SimplexNoise(random);
            riverShape = new SimplexNoise(random);
        }

        public double riverWeight(int x, int z) {
            return ease((riverRegions.get(x / 1250.0, z / 1250.0) + .28) / .46);
        }

        private River river(int x, int z, int lava, double rough, double broad) {
            double wx = x + 26 * riverWarp.get(x / 115.0, z / 115.0);
            double wz = z + 26 * riverShape.get(x / 115.0, z / 115.0);
            // Two warped families of continuous channels meet at junctions. Sampling
            // their union keeps forks connected instead of generating isolated pits.
            double a = Math.abs(Math.sin((wx + .36 * wz) / 37)) * 34;
            double b = Math.abs(Math.sin((wz - .27 * wx) / 46 + 1.7)) * 42;
            double distance = Math.min(a, b);
            double crack = 3.4 + .8 * broad;
            double bank = ease((distance - crack) / 3);
            double cliff = ease((distance - crack - 7) / 13);
            double floor = lava - 5 + bank * 7 + cliff * (20 + 4 * broad) + rough * .35 * bank;
            double width = 18 + 2 * broad;
            double open = ease((riverShape.get(x / 260.0, z / 260.0) - .05) / .55);
            double roof = mix(lava + 16 + 3 * broad - distance * .16, 118, open);
            return new River(distance, width, floor, roof, lava + 36 + 8 * broad + 2 * rough);
        }

        public double bridgeWeight(int x, int z) { return ease((regions.get(x / 650.0, z / 650.0) + .12) / .48); }
        private double baseIslandWeight(int x, int z) { return ease((patches.get(x / 210.0, z / 210.0) - .30) / .32); }
        public double islandWeight(int x, int z) {
            double weight = baseIslandWeight(x, z);
            if (weight == 1) return weight;
            int gx = Math.floorDiv(x, 160), gz = Math.floorDiv(z, 160);
            for (int dz = -1; dz <= 1; dz++) for (int dx = -1; dx <= 1; dx++) {
                FlatIsland island = flatIsland(gx + dx, gz + dz);
                if (!island.present || baseIslandWeight((int) island.x, (int) island.z) < .8) continue;
                // Give a selected large island enough island-style cavern around its
                // entire footprint; blend back into the regional mask beyond its shore.
                weight = Math.max(weight, ease((island.radius + 28 - Math.hypot(x - island.x, z - island.z)) / 20));
            }
            return weight;
        }
        public int lavaLevel(int x, int z) { return lake(x, z).level; }

        @FunctionalInterface
        public interface Opening { boolean test(int x, int z); }

        /** A lip must reach the pool interior through face-connected surface lava.
         * A bounded search crosses chunk edges mathematically, without loading chunks. */
        public boolean connectedToLake(int x, int z, int level, Opening opening) {
            if (lavaLevel(x, z) != level || !opening.test(x, z)) return false;
            if (lake(x, z).shoreDistance >= 6) return true;
            final int radius = 10, width = radius * 2 + 1;
            int[] queue = new int[width * width];
            boolean[] visited = new boolean[queue.length];
            byte[] steps = new byte[queue.length];
            int head = 0, tail = 1, center = radius * width + radius;
            queue[0] = center;
            visited[center] = true;
            while (head < tail) {
                int cell = queue[head++], cx = cell % width, cz = cell / width;
                int px = x + cx - radius, pz = z + cz - radius;
                if (lake(px, pz).shoreDistance >= 6) return true;
                // Bound path length, not just search area: every cell on a successful
                // route then has a successful suffix and remains actual source lava.
                if (steps[cell] >= radius) continue;
                for (int direction = 0; direction < 4; direction++) {
                    int nx = cx + (direction == 0 ? -1 : direction == 1 ? 1 : 0);
                    int nz = cz + (direction == 2 ? -1 : direction == 3 ? 1 : 0);
                    if (nx < 0 || nz < 0 || nx >= width || nz >= width) continue;
                    int next = nz * width + nx;
                    if (visited[next]) continue;
                    visited[next] = true;
                    int bx = x + nx - radius, bz = z + nz - radius;
                    if (lavaLevel(bx, bz) == level && opening.test(bx, bz)) {
                        steps[next] = (byte) (steps[cell] + 1);
                        queue[tail++] = next;
                    }
                }
            }
            return false;
        }

        private FlatIsland flatIsland(int gx, int gz) {
            long key = key(gx, gz);
            var cache = flatIslands.get();
            FlatIsland old = cache.getAndMoveToFirst(key);
            if (old != null) return old;
            RandomSource random = RandomSource.create(mixSeed(seed ^ key ^ 0x3a97ef16L));
            double x = (gx + .5) * 160 + (random.nextDouble() - .5) * 40;
            double z = (gz + .5) * 160 + (random.nextDouble() - .5) * 40;
            FlatIsland made = new FlatIsland(x, z, 26 + random.nextDouble() * 20, random.nextDouble() * TAU,
                lavaLevel((int) x, (int) z) + 1.5 + random.nextDouble(), random.nextFloat() < .6);
            if (cache.size() >= 512) cache.removeLast();
            cache.putAndMoveToFirst(key, made);
            return made;
        }

        private boolean nearFlatIsland(double x, double z) {
            int gx = (int) Math.floor(x / 160), gz = (int) Math.floor(z / 160);
            for (int dz = -1; dz <= 1; dz++) for (int dx = -1; dx <= 1; dx++) {
                FlatIsland island = flatIsland(gx + dx, gz + dz);
                if (island.present && Math.hypot(x - island.x, z - island.z) < island.radius + 22) return true;
            }
            return false;
        }

        private void flatIslands(List<Shelf> islets, int x, int z, double broad, double rough) {
            int gx = Math.floorDiv(x, 160), gz = Math.floorDiv(z, 160);
            for (int dz = -1; dz <= 1; dz++) for (int dx = -1; dx <= 1; dx++) {
                FlatIsland island = flatIsland(gx + dx, gz + dz);
                if (!island.present) continue;
                double rx = x - island.x + broad * 2, rz = z - island.z + rough;
                double angle = Math.atan2(rz, rx), distance = Math.hypot(rx, rz);
                double outline = island.radius + 3 * Math.sin(angle * 3 + island.phase)
                    + 1.5 * Math.sin(angle * 5 - island.phase);
                if (distance >= outline + 1) continue;
                // A broad gently undulating shelf, with only the outer shore rolling
                // down into the lava. Keep nearby boulder centers off the flat interior.
                double shore = ease((distance / outline - .65) / .35);
                double top = island.top + broad * .45 + rough * .15 - shore * 4;
                islets.add(new Shelf(outline - distance, top, 24));
            }
        }

        private Basin basin(int gx, int gz) {
            long key = key(gx, gz);
            var cache = basins.get();
            Basin old = cache.getAndMoveToFirst(key);
            if (old != null) return old;
            RandomSource random = RandomSource.create(mixSeed(seed ^ key ^ 0x72a914deL));
            // Every raised area contains a broad core. Thresholding arbitrary noise
            // extrema instead could produce arbitrarily small elevated puddles.
            Basin made = new Basin((gx + .5) * 320 + (random.nextDouble() - .5) * 80,
                (gz + .5) * 320 + (random.nextDouble() - .5) * 80,
                145 + random.nextDouble() * 45, .85 + random.nextDouble() * .3,
                random.nextDouble() * TAU, random.nextFloat() < .55 ? 80 + random.nextDouble() * 24 : 0);
            if (cache.size() >= 256) cache.removeLast();
            cache.putAndMoveToFirst(key, made);
            return made;
        }

        private Lake lake(int x, int z) {
            long key = key(x, z);
            var cache = lakeColumns.get();
            Lake old = cache.getAndMoveToFirst(key);
            if (old != null) return old;
            double wx = x + 8 * lakes.get(x / 170.0, z / 170.0);
            double wz = z + 8 * erosion.get(x / 170.0, z / 170.0);
            double outer = Double.NEGATIVE_INFINITY, inner = Double.NEGATIVE_INFINITY;
            int gx = Math.floorDiv(x, 320), gz = Math.floorDiv(z, 320);
            for (int dz = -1; dz <= 1; dz++) for (int dx = -1; dx <= 1; dx++) {
                Basin b = basin(gx + dx, gz + dz);
                double rx = wx - b.x, rz = wz - b.z;
                double u = rx * Math.cos(b.angle) + rz * Math.sin(b.angle);
                double v = (rz * Math.cos(b.angle) - rx * Math.sin(b.angle)) / b.aspect;
                double distance = Math.hypot(u, v);
                outer = Math.max(outer, b.radius - distance);
                if (b.core > 0) inner = Math.max(inner, b.core - distance);
            }
            int level = inner >= 0 ? 35 : outer >= 0 ? 32 : 29;
            double shore = inner >= 0 ? inner : outer >= 0 ? Math.min(outer, -inner) : -outer;
            Lake made = new Lake(level, shore);
            if (cache.size() >= 8192) cache.removeLast();
            cache.putAndMoveToFirst(key, made);
            return made;
        }

        private Node node(int gx, int gz) {
            long key = key(gx, gz);
            var cache = nodes.get();
            Node old = cache.getAndMoveToFirst(key);
            if (old != null) return old;
            RandomSource random = RandomSource.create(mixSeed(seed ^ key));
            double x = (gx + .5) * SPACING + (random.nextDouble() - .5) * 18;
            double z = (gz + .5) * SPACING + (random.nextDouble() - .5) * 18;
            boolean roof = random.nextFloat() < .18;
            int top = Math.round(51 + 6 * elevation.get(x / 420, z / 420)) + (roof ? 6 : 0);
            int tiers = roof ? 1 : random.nextFloat() < .5 ? 1 : random.nextFloat() < .7 ? 2 : 3;
            Node made = new Node(x, z, 3.8 + random.nextDouble() * 2.2, top,
                roof, random.nextFloat() < .7, random.nextDouble() * TAU,
                random.nextFloat() < .82, random.nextFloat() < .74, 6, tiers);
            if (cache.size() >= 512) cache.removeLast();
            cache.putAndMoveToFirst(key, made);
            return made;
        }

        private static Node tier(Node base, int tier) {
            if (tier == 0) return base;
            // Each narrower stem starts on the platform below; offsets remain inside
            // that platform, so stacked sections never become floating rock shelves.
            double shift = base.radius * .23 * tier;
            return new Node(base.x + Math.cos(base.phase) * shift, base.z + Math.sin(base.phase) * shift,
                base.radius * Math.pow(.78, tier), base.top + 18 * tier, false, false,
                base.phase + tier * .4, base.east, base.south, base.top + 18 * (tier - 1) - .5, 1);
        }

        private static void bridges(List<Shelf> spans, int x, int z, Node a, Node b, double rough, double broad) {
            for (int tier = 0; tier < Math.min(a.tiers, b.tiers); tier++)
                bridge(spans, x, z, tier(a, tier), tier(b, tier), rough, broad);
        }

        private static long mixSeed(long value) {
            value = (value ^ value >>> 30) * 0xbf58476d1ce4e5b9L;
            value = (value ^ value >>> 27) * 0x94d049bb133111ebL;
            return value ^ value >>> 31;
        }

        private Column column(int x, int z) {
            long key = key(x, z);
            var cache = columns.get();
            Column old = cache.getAndMoveToFirst(key);
            if (old != null) return old;
            double bridges = bridgeWeight(x, z), islands = islandWeight(x, z);
            double rivers = riverWeight(x, z);
            Lake lake = lake(x, z);
            int lava = lake.level, high = lava;
            for (int dz = -1; dz <= 1; dz++) for (int dx = -1; dx <= 1; dx++) {
                if (Math.abs(dx) + Math.abs(dz) == 1)
                    high = Math.max(high, lavaLevel(x + dx, z + dz));
            }
            // The high side has a solid bowl wall behind this one-column falling
            // veneer; most lips overflow, while occasional crust interrupts the sheet.
            int fallTop = high;
            double rough = detail.get(x / 12.0, z / 12.0);
            double broad = erosion.get(x / 29.0, z / 29.0);
            double interior = ease(lake.shoreDistance / 20);
            double bed = lava - 2 - interior * (7 + broad + rough * .4);
            if (lake.shoreDistance < 2.5 && erosion.get(x / 16.0, z / 16.0) > .42) bed = lava - 1;
            List<Pillar> pillars = new ArrayList<>();
            List<Shelf> spans = new ArrayList<>(), islets = new ArrayList<>();
            if (bridges > 0 && islands < 1 && rivers < 1) {
                int gx = Math.floorDiv(x, SPACING), gz = Math.floorDiv(z, SPACING);
                for (int dz = -1; dz <= 1; dz++) for (int dx = -1; dx <= 1; dx++) {
                    Node n = node(gx + dx, gz + dz);
                    for (int tier = 0; tier < n.tiers; tier++) {
                        Node section = tier(n, tier);
                        if (Math.hypot(x - section.x, z - section.z) < section.radius + 7)
                            pillars.add(new Pillar(x - section.x, z - section.z, rough, section));
                    }
                    if (n.east) bridges(spans, x, z, n, node(gx + dx + 1, gz + dz), rough, broad);
                    if (n.south) bridges(spans, x, z, n, node(gx + dx, gz + dz + 1), rough, broad);
                }
            }
            if (islands > 0 && rivers < 1) {
                flatIslands(islets, x, z, broad, rough);
                int gx = Math.floorDiv(x, 56), gz = Math.floorDiv(z, 56);
                for (int dz = -1; dz <= 1; dz++) for (int dx = -1; dx <= 1; dx++) {
                    RandomSource random = RandomSource.create(mixSeed(seed ^ key(gx + dx, gz + dz) ^ 0x749abdL));
                    double cx = (gx + dx + .5) * 56 + (random.nextDouble() - .5) * 18;
                    double cz = (gz + dz + .5) * 56 + (random.nextDouble() - .5) * 18;
                    if (nearFlatIsland(cx, cz)) continue;
                    double radius = 7 + random.nextDouble() * 5;
                    double phase = random.nextDouble() * TAU;
                    double dxIsland = x - cx + broad * 2, dzIsland = z - cz + rough * 1.2;
                    double angle = Math.atan2(dzIsland, dxIsland);
                    double distance = Math.hypot(dxIsland, dzIsland);
                    double outline = radius + 2 * Math.sin(angle * 3 + phase) + Math.sin(angle * 5 - phase);
                    if (distance < outline + 2) {
                        double top = lavaLevel((int) cx, (int) cz) + 7 + random.nextDouble() * 7
                            - 8 * Math.pow(distance / outline, 2) + 2 * broad + rough;
                        islets.add(new Shelf(outline + rough - distance, top, 35));
                    }
                }
            }
            Column made = new Column(bridges, islands, rivers, rivers > 0 ? river(x, z, lava, rough, broad) : null, lava, fallTop,
                bed, 113 + 4 * elevation.get(x / 150.0, z / 150.0) + broad, pillars, spans, islets);
            if (cache.size() >= 2048) cache.removeLast();
            cache.putAndMoveToFirst(key, made);
            return made;
        }

        private static void bridge(List<Shelf> spans, int x, int z, Node a, Node b, double rough, double broad) {
            if (x < Math.min(a.x, b.x) - 12 || x > Math.max(a.x, b.x) + 12
                    || z < Math.min(a.z, b.z) - 12 || z > Math.max(a.z, b.z) + 12) return;
            double dx = b.x - a.x, dz = b.z - a.z, length = Math.hypot(dx, dz);
            double bend = Math.sin(a.phase + b.phase) * 5;
            double best = Double.POSITIVE_INFINITY, along = 0;
            double px = a.x, pz = a.z;
            for (int step = 1; step <= 8; step++) {
                double t = step / 8.0;
                double offset = Math.sin(t * Math.PI) * bend;
                double qx = mix(a.x, b.x, t) - dz / length * offset;
                double qz = mix(a.z, b.z, t) + dx / length * offset;
                double sx = qx - px, sz = qz - pz;
                double u = Math.clamp(((x - px) * sx + (z - pz) * sz) / (sx * sx + sz * sz), 0, 1);
                double distance = Math.hypot(x - px - sx * u, z - pz - sz * u);
                if (distance < best) { best = distance; along = (step - 1 + u) / 8; }
                px = qx; pz = qz;
            }
            double middle = Math.sin(along * Math.PI);
            double shoulder = Math.pow(1 - middle, 2);
            double width = 1.65 + shoulder * (mix(a.radius, b.radius, along) * .65 - .65)
                + broad * .3 + rough * .2;
            if (best < width + 1) {
                double cross = Math.clamp(best / width, 0, 1);
                // Stay level through each landing before starting the slope: sloping
                // from the shaft centre leaves a sheer step at the platform edge.
                double slope = Math.clamp((along * length - a.radius - 1)
                    / (length - a.radius - b.radius - 2), 0, 1);
                double top = mix(a.top, b.top, slope) + Math.sin(slope * Math.PI) * (.7 + rough * .15 * cross);
                // Arched in length and rounded across the span, with erosion breaking
                // up the underside. Wide, deep shoulders grow directly into the pillars.
                double depth = (2.5 + 4 * shoulder + .7 * broad + rough * .4)
                    * (.45 + .55 * Math.sqrt(1 - cross * cross));
                spans.add(new Shelf(width - best, top, depth));
            }
        }

        private static double pillar(Pillar slice, int y) {
            Node n = slice.node;
            double height = n.roof ? (y - n.top) / 42.0 : (n.top - y) / Math.max(8, n.top - n.bottom);
            double lean = Math.sin(height * 2.4) * .7;
            double dx = slice.dx - Math.cos(n.phase) * lean;
            double dz = slice.dz - Math.sin(n.phase) * lean;
            double angle = Math.atan2(dz, dx), distance = Math.hypot(dx, dz);
            double lobes = .45 * Math.sin(angle * 3 + n.phase)
                + .25 * Math.sin(angle * 5 - n.phase + height * 1.8);
            double strata = .25 * Math.sin(height * 8 + n.phase) + slice.roughness * .2;
            double radius = n.radius + lobes + strata;
            double result;
            if (!n.roof) {
                double cap = .6 + .4 * ease((y - (n.top - 5)) / 4);
                double flare = 1.2 * ease((n.bottom + 10 - y) / 10);
                result = Math.min(radius * cap + flare - distance,
                    Math.min(n.top + .5 - y, y - n.bottom)) / 2;
            } else {
                // Narrow roof shaft leaves a walkable ring at its bridge-height landing.
                double shaft = radius * .65 + 2 * ease((height - .4) / .6);
                result = Math.min(shaft - distance, y - (n.top - 3.5)) / 3;
                result = Math.max(result, new Shelf(radius + .5 - distance, n.top, 3 + slice.roughness * .4).density(y));
                if (n.stairs) {
                    double stairAngle = (Math.atan2(slice.dz, slice.dx) - n.phase) % TAU;
                    if (stairAngle < 0) stairAngle += TAU;
                    int end = Math.min(110, n.top + 24);
                    for (int turn = 0; turn < 3; turn++) {
                        int top = n.top + (int) Math.floor(stairAngle / TAU * 8) + turn * 8;
                        double taper = ease((end - top) / 7.0);
                        if (top >= end || taper <= 0) continue;
                        double outer = shaft + 2.8 * taper;
                        double edge = Math.min(outer - distance, distance - (shaft - .8));
                        double stair = Math.min(edge, Math.min(top + .5 - y, y - (top - 2 * taper))) / 2;
                        result = Math.max(result, stair);
                    }
                }
            }
            return result;
        }

        public float density(int x, int y, int z, float vanilla) { return density(column(x, z), y, vanilla); }

        private static float density(Column column, int y, float vanilla) {
            if (y <= 5 || y >= 123) return vanilla;
            double vertical = ease((y - 5) / 9.0) * ease((123 - y) / 9.0);
            double result = vanilla;
            if (column.bridges > 0 || column.islands > 0) {
                double cavern = Math.max((column.floor - y) / 5, (y - column.ceiling) / 5);
                double bridges = cavern, islands = cavern;
                for (Pillar p : column.pillars) bridges = Math.max(bridges, pillar(p, y));
                for (Shelf s : column.spans) bridges = Math.max(bridges, s.density(y));
                for (Shelf s : column.islets) islands = Math.max(islands, s.density(y));
                result = mix(result, bounded(bridges), column.bridges * vertical);
                result = mix(result, bounded(islands), column.islands * vertical);
            }
            if (column.river != null) {
                double river = Math.max(column.river.density(y), (y - column.ceiling) / 5);
                result = mix(result, bounded(river), column.rivers * vertical);
            }
            // This also seals vanilla caverns beneath the pools. The bed slopes up
            // to one block below the lip, instead of filling the raised shelf with lava.
            if (y < column.lava) result = Math.max(result, (column.floor + .5 - y) / 3);
            return (float) result;
        }
    }

    private static final class Sampler implements DensitySampler {
        private final DensitySampler vanilla;
        private final Layout layout;

        private Sampler(DensitySampler vanilla, RandomSource random) {
            this.vanilla = vanilla;
            layout = new Layout(random);
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
