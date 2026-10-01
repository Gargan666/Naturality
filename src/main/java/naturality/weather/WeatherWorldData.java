package naturality.weather;

import com.mojang.serialization.Codec;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import naturality.Naturality;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/** All dimension weather pools stored with one save, rather than the installation config. */
public final class WeatherWorldData extends SavedData {
    public static final SavedDataType<WeatherWorldData> TYPE = new SavedDataType<>(
        Naturality.id("weather"), WeatherWorldData::new,
        Codec.unboundedMap(Codec.STRING, WeatherProfile.CODEC).xmap(WeatherWorldData::new, data -> data.profiles),
        DataFixTypes.LEVEL);

    private final ConcurrentHashMap<String, WeatherProfile> profiles;

    public WeatherWorldData() {
        profiles = new ConcurrentHashMap<>();
        profiles.put("minecraft:overworld", new WeatherProfile(true));
        profiles.put("minecraft:the_nether", new WeatherProfile(false));
        profiles.put("minecraft:the_end", new WeatherProfile(false));
    }

    private WeatherWorldData(Map<String, WeatherProfile> profiles) {
        this.profiles = new ConcurrentHashMap<>(profiles);
        if (this.profiles.isEmpty()) this.profiles.putAll(new WeatherWorldData().profiles);
    }

    public static WeatherWorldData get(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(TYPE);
    }

    public WeatherProfile profile(String dimension) {
        WeatherProfile existing = profiles.get(dimension);
        if (existing != null) return existing;
        WeatherProfile created = new WeatherProfile(false);
        WeatherProfile prior = profiles.putIfAbsent(dimension, created);
        if (prior != null) return prior;
        setDirty();
        return created;
    }

    public void importLegacy(@org.jspecify.annotations.Nullable Map<String, WeatherProfile> legacy) {
        if (legacy == null || legacy.isEmpty()) return;
        legacy.forEach((dimension, profile) -> {
            if (dimension != null && profile != null) profiles.put(dimension, profile);
        });
        setDirty();
    }

    public void setProfile(String dimension, WeatherProfile profile) {
        profiles.put(dimension, profile);
        setDirty();
    }

    public Map<String, WeatherProfile> profiles() { return Map.copyOf(profiles); }
}
