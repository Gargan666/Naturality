package naturality.weather;

import com.mojang.serialization.Codec;
import java.util.HashMap;
import java.util.Map;
import naturality.Naturality;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.util.datafix.DataFixTypes;

/** Runtime snapshots, separate from the existing per-world override formats. */
public final class EnvironmentWorldData extends SavedData {
    public static final Codec<Map<String, Map<String, Long>>> CODEC = Codec.unboundedMap(
        Codec.STRING, Codec.unboundedMap(Codec.STRING, Codec.LONG));
    public static final SavedDataType<EnvironmentWorldData> TYPE = new SavedDataType<>(
        Naturality.id("environment_state"), EnvironmentWorldData::new,
        CODEC.xmap(EnvironmentWorldData::new, data -> Map.copyOf(data.entries)), DataFixTypes.LEVEL);
    private final Map<String, Map<String, Long>> entries;
    public EnvironmentWorldData() { this(Map.of()); }
    private EnvironmentWorldData(Map<String, Map<String, Long>> entries) { this.entries=new HashMap<>(entries); }
    public static EnvironmentWorldData get(MinecraftServer server) { return server.getDataStorage().computeIfAbsent(TYPE); }
    public Map<String, Long> read(String key) { return entries.getOrDefault(key,Map.of()); }
    public void write(String key, Map<String, Long> value) {
        var snapshot=Map.copyOf(value);
        if(!snapshot.equals(entries.put(key,snapshot)))setDirty();
    }
    public Map<String, Map<String, Long>> entries() { return Map.copyOf(entries); }
    public void remove(String key) { if(entries.remove(key)!=null)setDirty(); }
    public static long bits(float value) { return Float.floatToIntBits(value); }
    public static float number(Map<String, Long> data,String key) { return Float.intBitsToFloat(data.getOrDefault(key,0L).intValue()); }
}
