package naturality.worldgen.overworld.ocean;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.longs.Long2DoubleLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import naturality.Naturality;
import naturality.bootstrap.ContentRegistration.NaturalityBlocks;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.CarvingMask;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.NoiseChunk;
import org.jspecify.annotations.Nullable;
import net.minecraft.util.Interval;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.densityfunction.*;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;

/** Seeded ocean relief, evaluated before surface rules, structures and decoration. */
@org.jspecify.annotations.NullMarked
public record OceanTerrain(DensityFunction input, DensityFunction continents) implements DensityFunction {
    /** The monument's rooms are nested pieces, not entries in the structure start. */
    public interface MonumentParts {
        java.util.List<? extends @org.jspecify.annotations.NonNull StructurePiece> naturality$rooms();
    }

    public static void placeMonument(StructurePiece piece,
            Structure.GenerationContext context) {
        if (!(context.chunkGenerator() instanceof net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator generator)
                || !generator.stable(net.minecraft.world.level.levelgen.NoiseGeneratorSettings.OVERWORLD)) return;
        var box = piece.getBoundingBox();
        int[] heights = new int[25];
        int index = 0;
        for (int z = 0; z < 5; z++) for (int x = 0; x < 5; x++) {
            heights[index++] = generator.getBaseHeight(box.minX() + 5 + x * 12,
                box.minZ() + 5 + z * 12, net.minecraft.world.level.levelgen.Heightmap.Types.OCEAN_FLOOR_WG,
                context.heightAccessor(), context.randomState());
        }
        java.util.Arrays.sort(heights);
        // Median follows the general floor rather than an isolated spire or ravine.
        // Keep the roof submerged and a small foundation clearance over that floor.
        int target = Math.min(heights[12] + 1, generator.getSeaLevel() - box.getYSpan() - 1);
        moveMonument(piece, target);
    }

    public static void moveMonument(StructurePiece piece, int baseY) {
        int dy = baseY - piece.getBoundingBox().minY();
        piece.move(0, dy, 0);
        if (piece instanceof MonumentParts parts) {
            for (var room : parts.naturality$rooms()) room.move(0, dy, 0);
        }
    }

    /** Vanilla carvers run after density and materials; keep a sealed layer below the -20 floor. */
    public static CarvingMask.Filter protectFloor(
            ChunkAccess chunk,
            NoiseChunk noise, DensityFunction continents,
            CarvingMask.@Nullable Filter previous) {
        return (x, y, z) -> (previous == null || previous.test(x, y, z))
            && (y < -24 || !ocean(noise.cachingSamplers().sampleValue(continents,
                chunk.getPos().getBlockX(x), 0, chunk.getPos().getBlockZ(z))));
    }

    /** Match vanilla ocean-biome sediment independently of its old preliminary surface height. */
    public static void surface(ChunkAccess chunk,
            BiomeManager biomes,
            NoiseChunk noise, DensityFunction continents, int seaLevel) {
        var pos = new BlockPos.MutableBlockPos();
        for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
            int wx = chunk.getPos().getBlockX(x), wz = chunk.getPos().getBlockZ(z);
            if (!ocean(noise.cachingSamplers().sampleValue(continents, wx, 0, wz))) continue;
            var biome = biomes.getBiome(pos.set(wx, seaLevel, wz));
            if (!biome.is(BiomeTags.IS_OCEAN)) continue;
            boolean sand = biome.is(Biomes.WARM_OCEAN)
                || biome.is(Biomes.LUKEWARM_OCEAN)
                || biome.is(Biomes.DEEP_LUKEWARM_OCEAN);
            // Descend through the actual open water, including frozen-ocean ice above it.
            boolean water = false;
            for (int y = seaLevel - 1; y >= -24; y--) {
                var state = chunk.getBlockState(pos.set(wx, y, wz));
                if (state.getFluidState().is(FluidTags.WATER)) { water = true; continue; }
                if (state.isAir() || state.is(BlockTags.ICE)) continue;
                if (!water) break; // Leave emergent spire tops to normal surface rules.
                for (int depth = 0; depth < 3; depth++) {
                    pos.set(wx, y - depth, wz);
                    var below = chunk.getBlockState(pos);
                    if (below.isAir() || !below.getFluidState().isEmpty()
                            || below.is(Blocks.BEDROCK)) break;
                    var material = sand && depth == 0 ? Blocks.SAND
                        : sand && depth == 1 ? NaturalityBlocks.SEDIMENT : Blocks.GRAVEL;
                    chunk.setBlockState(pos, material.defaultBlockState());
                }
                break;
            }
        }
    }

    public static final MapCodec<OceanTerrain> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
        DensityFunction.CODEC.fieldOf("input").forGetter(OceanTerrain::input),
        DensityFunction.CODEC.fieldOf("continents").forGetter(OceanTerrain::continents)
    ).apply(instance, OceanTerrain::new));

    @Override public DensitySampler compileSampler(CompileContext context) {
        return new Sampler(input.compileSampler(context), continents.compileSampler(context),
            context.createRandom(Naturality.id("ocean_terrain")));
    }
    @Override public DensityFunction rewriteChildren(DfRewriteRule rule) {
        return new OceanTerrain(rule.rewrite(input), rule.rewrite(continents));
    }
    @Override public Interval range() { return Interval.INFINITE; }
    @Override public int domainAxes() { return ALL_AXES; }
    @Override public MapCodec<OceanTerrain> codec() { return CODEC; }

    private static double ease(double value) {
        double t = Math.clamp(value, 0, 1);
        return t * t * t * (t * (t * 6 - 15) + 10);
    }

    public static boolean ocean(float continentalness) {
        // Preserve coasts and mushroom islands, which occupy the far-negative climate band.
        return continentalness < -.19 && continentalness > -1.05;
    }

    private static final class Sampler implements DensitySampler {
        private static final double[] COAST_DISTANCES = coastDistances();
        private final DensitySampler vanilla, continents;
        private final SimplexNoise warp, paths, cracks, relief;
        private final SimplexNoise[] regions = new SimplexNoise[5];
        private final long seed;
        // Only seed-dependent climate values are cached, never chunk/blending contexts.
        private final ThreadLocal<Long2DoubleLinkedOpenHashMap> distances =
            ThreadLocal.withInitial(Long2DoubleLinkedOpenHashMap::new);
        // Geometry depends only on this sampler's seed and the 96-block cell.
        // Keep it worker-local and bounded; never cache the context-dependent base height.
        private final ThreadLocal<Long2ObjectLinkedOpenHashMap<Spire>> spires =
            ThreadLocal.withInitial(Long2ObjectLinkedOpenHashMap::new);

        private static double[] coastDistances() {
            double[] values = new double[25 * 25];
            int index = 0;
            for (int dz = -12; dz <= 12; dz++) for (int dx = -12; dx <= 12; dx++)
                values[index++] = Math.hypot(dx, dz) * 64;
            return values;
        }

        Sampler(DensitySampler vanilla, DensitySampler continents, RandomSource random) {
            this.vanilla = vanilla;
            this.continents = continents;
            warp = new SimplexNoise(random);
            paths = new SimplexNoise(random);
            cracks = new SimplexNoise(random);
            relief = new SimplexNoise(random);
            for (int i = 0; i < regions.length; i++) regions[i] = new SimplexNoise(random);
            seed = random.nextLong();
        }

        private double coastGrid(SamplerContext context, int gx, int gz) {
            long key = ((long)gx << 32) ^ (gz & 0xffffffffL);
            var cache = distances.get();
            if (cache.containsKey(key)) return cache.get(key);
            double nearest = 768;
            // Bounded geometric distance to land in the vanilla continental field, in blocks.
            // The 64-block lattice is interpolated below; it never loads or generates a chunk.
            int index = 0;
            for (int dz = -12; dz <= 12; dz++) for (int dx = -12; dx <= 12; dx++) {
                double distance = COAST_DISTANCES[index++];
                if (distance >= nearest) continue;
                if (!ocean(continents.sampleValue(context, (gx + dx) * 64, 0, (gz + dz) * 64))) {
                    nearest = distance;
                }
            }
            if (cache.size() >= 4096) cache.removeFirstDouble();
            cache.put(key, nearest);
            return nearest;
        }

        private double coastDistance(SamplerContext context, int x, int z) {
            int gx = Math.floorDiv(x, 64), gz = Math.floorDiv(z, 64);
            double u = ease(Math.floorMod(x, 64) / 64.0), v = ease(Math.floorMod(z, 64) / 64.0);
            double a = coastGrid(context, gx, gz), b = coastGrid(context, gx + 1, gz);
            double c = coastGrid(context, gx, gz + 1), d = coastGrid(context, gx + 1, gz + 1);
            return (a + (b - a) * u) * (1 - v) + (c + (d - c) * u) * v;
        }

        private double surface(SamplerContext context, int x, int z) {
            // Find the original solid surface, rather than interpreting continentalness as height.
            for (int y = 128; y >= -32; y--) {
                if (vanilla.sampleValue(context, x, y, z) <= 0) continue;
                return y + .5;
            }
            return -32;
        }

        private record Column(double height, double blend) {}
        private static final Column UNCHANGED_COLUMN = new Column(0, 0);

        private Column column(SamplerContext context, int x, int z, java.util.function.DoubleSupplier surface) {
            float continentalness = continents.sampleValue(context, x, 0, z);
            if (!ocean(continentalness)) return UNCHANGED_COLUMN;
            double original = surface.getAsDouble();
            double blend = ease((-.19 - continentalness) / .09) * ease((continentalness + 1.05) / .12)
                * ease((62 - original) / 12);
            if (blend == 0) return UNCHANGED_COLUMN;
            double distance = coastDistance(context, x, z);
            double offshore = ease(distance / 640);
            // Leave relief above the depth limit so deep paths/ravines remain visible.
            double deepFloor = -8 + Math.clamp((original - 40) * .35, -6, 6);
            double base = original + (Math.min(original, deepFloor) - original) * offshore;
            double wx = x + 35 * warp.get(x / 210.0, z / 210.0);
            double wz = z + 35 * warp.get(x / 210.0 + 47, z / 210.0 - 19);
            double[] weights = new double[5];
            double total = 0;
            for (int i = 0; i < weights.length; i++) {
                weights[i] = Math.exp(10 * regions[i].get(x / 4500.0, z / 4500.0));
                total += weights[i];
            }
            double path = 15 * (1 - ease((Math.abs(paths.get(wx / 100, wz / 100)) - .045) / .16));
            double crack = 29 * (1 - ease((Math.abs(cracks.get(wx / 220, wz / 220)) - .018) / .09));
            double level = base + 12 * relief.get(wx / 170, wz / 170);
            double terrace = Math.floor(level / 12) * 12;
            // Broad landings, with a narrow sloped connector that voxelizes into stairs.
            double step = terrace + 12 * ease((level / 12 - Math.floor(level / 12) - .68) / .28);
            double shaped = (weights[0] * base + weights[1] * Math.max(-20, base - path)
                + weights[2] * spires(x, z, base) + weights[3] * Math.max(-20, base - crack)
                + weights[4] * Math.max(-20, step)) / total;
            // Beach is a coastal style, not a random inland patch: broad shallow shelves with
            // only a small amount of vanilla relief, fading out between 128 and 320 blocks.
            double beach = 58 - 10 * ease(distance / 320)
                + Math.clamp((original - 48) * .08, -2, 1);
            double offshoreStyles = ease((distance - 128) / 192);
            return new Column(Math.max(-20, beach + (shaped - beach) * offshoreStyles), blend);
        }

        private double spires(double x, double z, double base) {
            int gx = (int)Math.floor(x / 96), gz = (int)Math.floor(z / 96);
            double height = base;
            var cache = spires.get();
            for (int dz = -1; dz <= 1; dz++) for (int dx = -1; dx <= 1; dx++) {
                int cellX = gx + dx, cellZ = gz + dz;
                long key = ((long)cellX << 32) ^ (cellZ & 0xffffffffL);
                var spire = cache.get(key);
                if (spire == null) {
                    spire = Spire.create(seed, cellX, cellZ);
                    if (cache.size() >= 256) cache.removeFirst();
                    cache.put(key, spire);
                }
                height = spire.height(x, z, base, height);
            }
            return height;
        }

        private record Tier(double width, double depth, double offsetU, double offsetV, double fraction) {}

        private record Spire(double cx, double cz, double radius, double summit, double pedestal,
                double cos, double sin, double aspect, Tier[] tiers) {
            private static Spire create(long seed, int gx, int gz) {
                // Preserve the original random draw order, including the rare-summit branch.
                RandomSource random = RandomSource.create(seed ^ ((long)gx * 341873128712L)
                    ^ ((long)gz * 132897987541L));
                double cx = (gx + .15 + .7 * random.nextDouble()) * 96;
                double cz = (gz + .15 + .7 * random.nextDouble()) * 96;
                double radius = 8 + 31 * Math.pow(random.nextDouble(), 2);
                // Preserve the narrow stacks; compress only the broad tail into a 16-block
                // half-width limit so its upper tiers cannot become large mesas.
                if (radius > 12) radius = 12 + 4 * Math.tanh((radius - 12) / 4);
                // Absolute summit elevations keep ordinary stacks submerged even on shallow floors.
                // Only two percent of candidates can emerge, before regional blending lowers them.
                double summit = random.nextDouble() < .02
                    ? 68 + 12 * random.nextDouble() : 22 + 33 * random.nextDouble();
                double pedestal = random.nextDouble();
                double angle = random.nextDouble() * Math.PI;
                double cos = Math.cos(angle), sin = Math.sin(angle);
                double aspect = .65 + .55 * random.nextDouble();
                double middleLength = 1.5 + .5 * random.nextDouble();
                double upperLength = middleLength + .6 + .6 * random.nextDouble();
                double totalLength = 1 + middleLength + upperLength;
                double footFraction = pedestal > .35 ? .18 : 0;
                double elevation = footFraction;
                double offsetU = 0, offsetV = 0;
                double previousWidth = radius + (pedestal > .35 ? 6 * pedestal : 0);
                double previousDepth = radius * aspect + (pedestal > .35 ? 5 * pedestal : 0);
                Tier[] tiers = new Tier[3];
                for (int tier = 0; tier < 3; tier++) {
                    double width = radius * (1 - tier * .25);
                    double depth = width * aspect;
                    // Sample the whole containment range in the parent's local axes. Accumulated
                    // offsets keep every upper footprint supported without forcing concentric tiers.
                    offsetU += (2 * random.nextDouble() - 1) * (previousWidth - width);
                    offsetV += (2 * random.nextDouble() - 1) * (previousDepth - depth);
                    previousWidth = width;
                    previousDepth = depth;
                    double length = tier == 0 ? 1 : tier == 1 ? middleLength : upperLength;
                    elevation += (1 - footFraction) * length / totalLength;
                    tiers[tier] = new Tier(width, depth, offsetU, offsetV, tier == 2 ? 1 : elevation);
                }
                return new Spire(cx, cz, radius, summit, pedestal, cos, sin, aspect, tiers);
            }

            private double height(double x, double z, double base, double height) {
                double u = (x - cx) * cos + (z - cz) * sin;
                double v = (z - cz) * cos - (x - cx) * sin;
                double rise = Math.max(0, summit - base);
                // Unwarped rectangular tiers and the original one-block bevel.
                if (pedestal > .35) {
                    double edge = Math.min(radius + 6 * pedestal - Math.abs(u), radius * aspect + 5 * pedestal - Math.abs(v));
                    height = Math.max(height, base + rise * .18 * Math.clamp(edge, 0, 1));
                }
                for (var tier : tiers) {
                    double edge = Math.min(tier.width - Math.abs(u - tier.offsetU), tier.depth - Math.abs(v - tier.offsetV));
                    double landing = base + rise * tier.fraction;
                    height = Math.max(height, base + (landing - base) * Math.clamp(edge, 0, 1));
                }
                return height;
            }
        }

        private static float shape(Column column, int y, float original) {
            if (column.blend == 0) return original;
            // Preserve deep caves and bedrock; seal a small floor thickness under the open ocean.
            double influence = column.blend * ease((y + 40) / 16.0);
            double density = Math.clamp((column.height - y) / 8, -1, 1);
            return (float)(original + influence * (density - original));
        }

        @Override public float sampleValue(SamplerContext context, int x, int y, int z) {
            return shape(column(context, x, z, () -> surface(context, x, z)), y, vanilla.sampleValue(context, x, y, z));
        }

        @Override public void sampleVolume(SamplerContext context, DensityBuffer output, DensityVolume volume) {
            vanilla.sampleVolume(context, output, volume);
            int index = 0;
            for (int z = 0; z < volume.sizeZ(); z++) for (int x = 0; x < volume.sizeX(); x++) {
                int blockX = volume.blockX(x), blockZ = volume.blockZ(z);
                int columnIndex = index;
                Column column = column(context, blockX, blockZ, () -> {
                    // Reuse the already computed vanilla volume instead of re-running its graph
                    // for every height probe. Partial/sparse requests use the same exact fallback.
                    if (volume.minBlockY() <= -32 && volume.maxBlockY() >= 128
                            && volume.sizeY() > 1 && volume.blockY(1) - volume.blockY(0) == 1) {
                        for (int y = 128; y >= -32; y--) {
                            if (output.get(columnIndex + y - volume.minBlockY()) > 0) return y + .5;
                        }
                        return -32;
                    }
                    return surface(context, blockX, blockZ);
                });
                // Vanilla already filled this column. Rewriting it through shape's
                // identity branch needlessly touches every density in inland chunks.
                if (column.blend == 0) {
                    index += volume.sizeY();
                    continue;
                }
                for (int y = 0; y < volume.sizeY(); y++) {
                    output.set(index, shape(column, volume.blockY(y), output.get(index)));
                    index++;
                }
            }
        }
    }
}
