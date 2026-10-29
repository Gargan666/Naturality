package naturality.worldgen.nether.lavalakes;

import com.mojang.serialization.MapCodec;
import it.unimi.dsi.fastutil.longs.Long2BooleanOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
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
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.VerticalAnchor;
import net.minecraft.world.level.levelgen.densityfunction.*;
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
        // Retain the original seed identifier so existing lake regions line up.
        return new Sampler(input.compileSampler(context), context.createRandom(Naturality.id("nether_terrain")));
    }
    @Override public DensityFunction rewriteChildren(DfRewriteRule rule) {
        return new NetherLavaLakes(rule.rewrite(input));
    }
    @Override public Interval range() { return Interval.INFINITE; }
    @Override public int domainAxes() { return ALL_AXES; }
    @Override public MapCodec<NetherLavaLakes> codec() { return CODEC; }

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
            // Context-specific density must never go into the shared layout caches.
            private final Long2BooleanOpenHashMap connectedSources = new Long2BooleanOpenHashMap();

            @Override public @Nullable BlockState computeSubstance(int x, int y, int z, double density) {
                update = false;
                if (density > 0 || y < 5 || y >= 251) {
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
            // Protect only the basin bed; vanilla cave carving continues above it.
            return y > column.floor + .5;
        };
    }

    private record Basin(double x, double z, double radius, double aspect, double angle, double core) {}
    private record Lake(int level, double shoreDistance) {}
    private record Column(int lava, int fallTop, double floor) {}

    /** Immutable lake noise with bounded, worker-local caches; never reads or loads chunks. */
    public static final class Layout {
        private static double ease(double t) {
            t = Math.clamp(t, 0, 1);
            return t * t * t * (t * (t * 6 - 15) + 10);
        }
        private static long key(int x, int z) { return (long) x << 32 ^ (z & 0xffffffffL); }
        private static final double TAU = Math.PI * 2;
        private final SimplexNoise lakes, erosion, detail;
        private final long seed;
        private final ThreadLocal<Long2ObjectLinkedOpenHashMap<Column>> columns =
            ThreadLocal.withInitial(() -> new Long2ObjectLinkedOpenHashMap<>(1024));
        private final ThreadLocal<Long2ObjectLinkedOpenHashMap<Basin>> basins =
            ThreadLocal.withInitial(() -> new Long2ObjectLinkedOpenHashMap<>(128));
        private final ThreadLocal<Long2ObjectLinkedOpenHashMap<Lake>> lakeColumns =
            ThreadLocal.withInitial(() -> new Long2ObjectLinkedOpenHashMap<>(2048));

        public Layout(RandomSource random) {
            // Preserve the existing lakes' seed stream after removing the three
            // unrelated noise fields. GradientNoise in 26.3 consumes these draws.
            skipLegacyNoise(random);
            skipLegacyNoise(random);
            lakes = new SimplexNoise(random);
            erosion = new SimplexNoise(random);
            detail = new SimplexNoise(random);
            skipLegacyNoise(random);
            seed = random.nextLong();
        }

        private static void skipLegacyNoise(RandomSource random) {
            for (int offset = 0; offset < 3; offset++) random.nextDouble();
            for (int permutation = 0; permutation < 256; permutation++) random.nextInt(256 - permutation);
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
            int level = inner >= 0 ? 76 : outer >= 0 ? 64 : 52;
            double shore = inner >= 0 ? inner : outer >= 0 ? Math.min(outer, -inner) : -outer;
            Lake made = new Lake(level, shore);
            if (cache.size() >= 8192) cache.removeLast();
            cache.putAndMoveToFirst(key, made);
            return made;
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
            double bed = lava - 2 - interior * (14 + broad * 2 + rough * .8);
            if (lake.shoreDistance < 2.5 && erosion.get(x / 16.0, z / 16.0) > .42) bed = lava - 1;
            Column made = new Column(lava, fallTop, bed);
            if (cache.size() >= 2048) cache.removeLast();
            cache.putAndMoveToFirst(key, made);
            return made;
        }

        public float density(int x, int y, int z, float vanilla) { return density(column(x, z), y, vanilla); }

        private static float density(Column column, int y, float vanilla) {
            // Leave vanilla cavern geometry intact. Only add the solid bowl beneath
            // each pool, including its shallow lip and occasional exposed crust.
            if (y <= 5 || y >= column.lava) return vanilla;
            return (float) Math.max(vanilla, (column.floor + .5 - y) / 3);
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
