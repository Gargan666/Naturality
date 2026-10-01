package naturality.sky;

import com.mojang.serialization.Codec;
import java.util.HashMap;
import java.util.Map;
import naturality.Naturality;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/** Per-save overrides, with independent event settings for each dimension. */
public final class SkyEventWorldData extends SavedData {
    public static final SavedDataType<SkyEventWorldData> TYPE = new SavedDataType<>(
        Naturality.id("sky_events"), SkyEventWorldData::new,
        Codec.unboundedMap(Codec.STRING, Codec.unboundedMap(Codec.STRING, SkyEventSettings.CODEC))
            .xmap(SkyEventWorldData::new, data -> data.settings),
        DataFixTypes.LEVEL);

    private final Map<String, Map<String, SkyEventSettings>> settings;

    public SkyEventWorldData() { settings = new HashMap<>(); }

    private SkyEventWorldData(Map<String, Map<String, SkyEventSettings>> saved) {
        this();
        saved.forEach((dimension, events) -> settings.put(dimension, new HashMap<>(events)));
    }

    public static SkyEventWorldData get(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(TYPE);
    }

    public SkyEventSettings settings(ServerLevel level, SkyEventType type) {
        String dimension = level.dimension().identifier().toString();
        var events = settings.computeIfAbsent(dimension, _ -> new HashMap<>());
        return events.computeIfAbsent(type.id, _ -> new SkyEventSettings());
    }

    public void override(ServerLevel level, SkyEventType type, boolean enabled, int strength) {
        var event = settings(level, type);
        event.override = enabled;
        event.strength = Math.clamp(strength, 0, 20);
        setDirty();
        level.getServer().getDataStorage().scheduleSave();
    }
}
