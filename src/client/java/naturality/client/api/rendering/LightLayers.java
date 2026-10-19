package naturality.client.api.rendering;

/** Snapshot-safe light-layer composition shared by optical sky and virtual block light. */
@org.jspecify.annotations.NullMarked
public final class LightLayers {
    private LightLayers() {}

    public static net.minecraft.world.level.chunk.DataLayer mergeLightData(
            net.minecraft.world.level.chunk.DataLayer vanilla, net.minecraft.world.level.chunk.DataLayer extra) {
        // Retain a fresh snapshot, without a persistent cache of mutable layers.
        if (vanilla == extra || extra.isEmpty() || vanilla.isDefinitelyFilledWith(15)) return vanilla.copy();
        if (vanilla.isEmpty() || extra.isDefinitelyFilledWith(15)) return extra.copy();
        if (vanilla.isDefinitelyHomogenous() && extra.isDefinitelyHomogenous())
            return new net.minecraft.world.level.chunk.DataLayer(Math.max(vanilla.get(0, 0, 0), extra.get(0, 0, 0)));
        var merged = vanilla.copy();
        byte[] target = merged.getData();
        // Do not materialize homogeneous input storage: only the output is mutable.
        byte[] additional = extra.isDefinitelyHomogenous() ? null : extra.getData();
        int uniform = additional == null ? extra.get(0, 0, 0) * 17 : 0;
        for (int i = 0; i < target.length; i++) {
            int a = target[i] & 255, b = additional == null ? uniform : additional[i] & 255;
            target[i] = (byte)(Math.max(a & 15, b & 15) | Math.max(a & 240, b & 240));
        }
        return merged;
    }
}
