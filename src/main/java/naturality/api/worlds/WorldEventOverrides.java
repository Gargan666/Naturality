package naturality.api.worlds;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/** Shared per-dimension override storage for sky and gameplay event systems. */
@org.jspecify.annotations.NullMarked
public abstract class WorldEventOverrides extends SavedData {
    public static final class Settings {
        public volatile boolean override;
        public volatile int strength;
        public Settings(boolean override, int strength) {
            this.override = override;
            this.strength = Math.clamp(strength, 0, 20);
        }
        public static Codec<Settings> codec(int defaultStrength) {
            return RecordCodecBuilder.create(instance -> instance.group(
                Codec.BOOL.optionalFieldOf("override", false).forGetter(settings -> settings.override),
                Codec.INT.optionalFieldOf("strength", defaultStrength).forGetter(settings -> settings.strength)
            ).apply(instance, Settings::new));
        }
    }
    private final Map<String, Map<String, Settings>> entries = new HashMap<>();
    protected WorldEventOverrides(Map<String, Map<String, Settings>> saved) {
        saved.forEach((dimension, events) -> entries.put(dimension, new HashMap<>(events)));
    }
    public static Codec<Map<String, Map<String, Settings>>> codec(int defaultStrength) {
        return Codec.unboundedMap(Codec.STRING, Codec.unboundedMap(Codec.STRING, Settings.codec(defaultStrength)));
    }
    protected Map<String, Map<String, Settings>> entries() { return entries; }
    public @org.jspecify.annotations.Nullable Settings takeLegacyEvent(String dimension, String event) {
        var events = entries.get(dimension);
        var legacy = events == null ? null : events.remove(event);
        if (legacy != null) setDirty();
        return legacy;
    }
    protected Settings settings(ServerLevel level, String event, int defaultStrength, int maximum) {
        var events = entries.computeIfAbsent(level.dimension().identifier().toString(), _ -> new HashMap<>());
        var settings = events.computeIfAbsent(event, _ -> {
            var initial = initial(level, event, defaultStrength);
            setDirty();
            return initial;
        });
        int bounded = Math.clamp(settings.strength, 0, maximum);
        if (settings.strength != bounded) { settings.strength = bounded; setDirty(); }
        return settings;
    }
    protected Settings initial(ServerLevel level, String event, int defaultStrength) {
        return new Settings(false, defaultStrength);
    }
    protected void override(ServerLevel level, String event, boolean enabled, int strength, int maximum) {
        var settings = settings(level, event, maximum, maximum);
        settings.override = enabled;
        settings.strength = Math.clamp(strength, 0, maximum);
        setDirty();
        Objects.requireNonNull(level.getServer()).getDataStorage().scheduleSave();
    }
}
