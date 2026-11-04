package naturality.worldgen.nether.gilded;

import naturality.Naturality;
import net.fabricmc.fabric.api.biome.v1.NetherBiomes;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.Climate;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.LinkedHashMap;
import java.util.BitSet;
import java.util.function.Supplier;
import net.minecraft.world.level.levelgen.densityfunction.DensitySampler;

/** Small, isolated forest pockets; no world/chunk queries during biome selection. */
@org.jspecify.annotations.NullMarked
public final class GildedForest {
    public static final ResourceKey<Biome> BIOME = ResourceKey.create(Registries.BIOME, Naturality.id("gilded_forest"));
    private static final Map<Climate.Sampler, Supplier<DensitySampler.Bound>> TERRAIN =
        Collections.synchronizedMap(new WeakHashMap<>());

    public static void terrain(Climate.Sampler climate, Supplier<DensitySampler.Bound> density) {
        TERRAIN.put(climate, density);
    }

    private GildedForest() {}

    public static void initialize() {
        // Make the holder available to the Nether source without winning climate
        // selection: wastes has the identical point with a lower offset penalty.
        NetherBiomes.addNetherBiome(BIOME, Climate.parameters(0, 0, 0, 0, 0, 0, 1));
    }

    public static BiomeResolver wrap(BiomeSource source, Climate.Sampler sampler, BiomeResolver original) {
        Holder<Biome> gilded = source.possibleBiomes().stream().filter(b -> b.is(BIOME)).findFirst().orElse(null);
        if (gilded == null) return original;
        var terrain = TERRAIN.get(sampler);
        if (terrain == null) return original;
        var density = terrain.get();
        // Resolver-local bounded cache: neighbouring vertical biome cells reuse one
        // survey, without retaining worlds or requesting any generated chunks.
        Map<Long, BitSet> floors = new LinkedHashMap<>();
        // A fixed sample of seeded climate noise provides a stable per-world salt.
        long salt = mix(Double.doubleToLongBits(sampler.temperature().sampleValue(173, 0, -491))
            ^ Double.doubleToLongBits(sampler.humidity().sampleValue(-617, 0, 239)));
        return (x, y, z) -> {
            var selected = original.getNoiseBiome(x, y, z);
            if ((selected.is(Biomes.CRIMSON_FOREST) || selected.is(Biomes.NETHER_WASTES))
                    && contains(salt, x * 4, z * 4)) {
                long column = ((long)x << 32) ^ (z & 0xffffffffL);
                BitSet heights = floors.get(column);
                if (heights == null) {
                    heights = habitableHeights(density, x * 4, z * 4);
                    if (floors.size() >= 512) floors.clear();
                    floors.put(column, heights);
                }
                if (y >= 0 && heights.get(y)) return gilded;
            }
            return selected;
        };
    }

    private static BitSet habitableHeights(DensitySampler.Bound density, int x, int z) {
        BitSet heights = new BitSet(64);
        // Stay above the highest terraced lava level (76) and below roof bedrock.
        // Require a small continuous floor and twenty blocks of canopy clearance.
        for (int floor = 80; floor <= 228; floor++) {
            if (density.sampleValue(x, floor, z) <= 0
                    || density.sampleValue(x, floor + 1, z) > 0) continue;
            boolean open = true;
            for (int dx = -4; dx <= 4 && open; dx += 4) for (int dz = -4; dz <= 4 && open; dz += 4) {
                if (density.sampleValue(x + dx, floor - 2, z + dz) <= 0) { open = false; break; }
                for (int dy = 3; dy <= 20; dy++) {
                    if (density.sampleValue(x + dx, floor + dy, z + dz) > 0) { open = false; break; }
                }
            }
            if (open) heights.set(Math.max(0, (floor - 4) >> 2), ((floor + 20) >> 2) + 1);
        }
        return heights;
    }

    public static boolean contains(long salt, int x, int z) {
        // One candidate per 256-block cell, with only one in sixteen enabled.
        // Centres stay far from cell borders, preventing neighbouring patches merging.
        int cellX = Math.floorDiv(x, 256), cellZ = Math.floorDiv(z, 256);
        long hash = mix(salt ^ cellX * 0x632BE59BD9B4E019L ^ cellZ * 0x9E3779B97F4A7C15L);
        if ((hash & 15) != 0) return false;
        int centerX = cellX * 256 + 64 + (int)((hash >>> 8) & 127);
        int centerZ = cellZ * 256 + 64 + (int)((hash >>> 16) & 127);
        int dx = x - centerX, dz = z - centerZ;
        // Small patches dominate. Thirty-block maximum radius leaves room for
        // vanilla quart-biome smoothing while keeping diameter around seventy.
        double size = ((hash >>> 24) & 255) / 255.0;
        double radius = 12 + 18 * size * size;
        double angle = Math.atan2(dz, dx);
        double edge = radius * (.88 + .07 * Math.sin(3 * angle + (hash & 255))
            + .05 * Math.sin(5 * angle));
        return (double)dx * dx + (double)dz * dz <= edge * edge;
    }

    private static long mix(long value) {
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }
}
