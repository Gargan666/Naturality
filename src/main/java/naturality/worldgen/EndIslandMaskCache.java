package naturality.worldgen;

import it.unimi.dsi.fastutil.longs.Long2FloatLinkedOpenHashMap;
import net.minecraft.world.level.levelgen.densityfunction.*;

/** End island density is independent of Y; preserve its exact value across vertical probes. */
public final class EndIslandMaskCache implements DensitySampler {
    private final DensitySampler source;
    private final ThreadLocal<Long2FloatLinkedOpenHashMap> values = ThreadLocal.withInitial(() -> {
        var cache = new Long2FloatLinkedOpenHashMap(4096);
        cache.defaultReturnValue(Float.NaN);
        return cache;
    });
    public EndIslandMaskCache(DensitySampler source) { this.source = source; }
    @Override public float sampleValue(SamplerContext context,int x,int y,int z) {
        // Vanilla truncates block coordinates to an 8-block grid (including negatives).
        // All positions in that cell have exactly the same island density.
        long key = ((long)(x / 8) << 32) ^ ((z / 8) & 0xffffffffL);
        var cache = values.get();
        float saved = cache.get(key);
        if (!Float.isNaN(saved)) return saved;
        float value = source.sampleValue(context,x,y,z);
        if (cache.size() >= 4096) cache.removeFirstFloat();
        cache.put(key,value);
        return value;
    }
    @Override public void sampleVolume(SamplerContext context,DensityBuffer output,DensityVolume volume) {
        for (int z = 0; z < volume.sizeZ(); z++) for (int x = 0; x < volume.sizeX(); x++) {
            float value = sampleValue(context, volume.blockX(x), 0, volume.blockZ(z));
            output.setRange(volume.indexUnchecked(x, 0, z), volume.sizeY(), value);
        }
    }
}
