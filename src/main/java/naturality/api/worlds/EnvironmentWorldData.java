package naturality.api.worlds;

import com.mojang.serialization.Codec;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import naturality.Naturality;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.util.datafix.DataFixTypes;

/** Runtime snapshots, separate from the existing per-world override formats. */
@org.jspecify.annotations.NullMarked
public final class EnvironmentWorldData extends SavedData {
    public static final Codec<Map<String, Map<String, Long>>> CODEC = Codec.unboundedMap(
        Codec.STRING, Codec.unboundedMap(Codec.STRING, Codec.LONG));
    public static final SavedDataType<EnvironmentWorldData> TYPE = new SavedDataType<>(
        Naturality.id("environment_state"), EnvironmentWorldData::new,
        CODEC.xmap(EnvironmentWorldData::new, EnvironmentWorldData::entries), DataFixTypes.LEVEL);
    private final Map<String, Map<String, Long>> entries;
    private final Map<String, Supplier<Map<String, Long>>> pending = new HashMap<>();
    public EnvironmentWorldData() { this(Map.of()); }
    private EnvironmentWorldData(Map<String, Map<String, Long>> entries) {
        this.entries = new HashMap<>();
        entries.forEach((key, value) -> this.entries.put(key, Map.copyOf(value)));
    }
    public static EnvironmentWorldData get(MinecraftServer server) { return server.getDataStorage().computeIfAbsent(TYPE); }
    public Map<String, Long> read(String key) {
        var snapshot = pending.get(key);
        if (snapshot != null) {
            store(key, snapshot.get());
            pending.remove(key);
        }
        return entries.getOrDefault(key, Map.of());
    }

    /**
     * Coalesces tick updates until a read or save needs an immutable snapshot. Suppliers run on
     * the server thread and must only read their cycle state, without retaining a world/server
     * or calling back into this data. A later write, remove or defer supersedes the supplier.
     */
    public void defer(String key, Supplier<Map<String, Long>> snapshot) {
        pending.put(key, snapshot);
    }

    public void write(String key, Map<String, Long> value) {
        pending.remove(key);
        store(key, value);
    }

    private void store(String key, Map<String, Long> value) {
        var snapshot = Map.copyOf(value);
        if (!snapshot.equals(entries.put(key, snapshot))) setDirty();
    }

    private void flush() {
        var updates = pending.entrySet().iterator();
        while (updates.hasNext()) {
            var update = updates.next();
            store(update.getKey(), update.getValue().get());
            updates.remove();
        }
    }

    @Override
    public boolean isDirty() {
        // SavedDataStorage checks and encodes on the server thread before queuing disk I/O.
        // Materialize here so unchanged/frozen cycles do not force another disk write.
        flush();
        return super.isDirty();
    }

    public Map<String, Map<String, Long>> entries() {
        flush();
        return Map.copyOf(entries);
    }
    public void remove(String key) {
        pending.remove(key);
        if (entries.remove(key) != null) setDirty();
    }
    public static long bits(float value) { return Float.floatToIntBits(value); }
    public static float number(Map<String, Long> data,String key) { return Float.intBitsToFloat(data.getOrDefault(key,0L).intValue()); }
}
