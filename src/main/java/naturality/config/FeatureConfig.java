package naturality.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.LoggerFactory;

/** No Minecraft classes: also safe to read before block/entity classes are transformed. */
public final class FeatureConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("naturality-features.json");
    private static final FeatureConfig INSTANCE = load();
    private static final Map<Feature, Boolean> ACTIVE = snapshot(INSTANCE);
    public Map<String, Boolean> features = new LinkedHashMap<>();

    public FeatureConfig() { for (var feature : Feature.values()) features.put(feature.key(), true); }
    public boolean get(Feature feature) { return !Boolean.FALSE.equals(features.get(feature.key())); }
    public void set(Feature feature, boolean value) { features.put(feature.key(), value); }
    public boolean effective(Feature feature) {
        return get(feature) && (feature.parent == null || effective(feature.parent));
    }
    public static FeatureConfig get() { return INSTANCE; }
    public static boolean active(Feature feature) { return Boolean.TRUE.equals(ACTIVE.get(feature)); }
    public boolean restartRequired() { return !ACTIVE.equals(snapshot(this)); }
    private static Map<Feature, Boolean> snapshot(FeatureConfig config) {
        var result = new EnumMap<Feature, Boolean>(Feature.class);
        for (var feature : Feature.values()) result.put(feature, config.effective(feature));
        return result;
    }
    private static FeatureConfig load() {
        if (Files.exists(FILE)) try (var reader = Files.newBufferedReader(FILE)) {
            var config = GSON.fromJson(reader, FeatureConfig.class);
            if (config != null && config.features != null) {
                for (var feature : Feature.values()) config.features.putIfAbsent(feature.key(), true);
                return config;
            }
        } catch (Exception error) {
            LoggerFactory.getLogger("naturality").error("Could not read feature config; using defaults", error);
        }
        return new FeatureConfig();
    }
    public void save() {
        try {
            Files.createDirectories(FILE.getParent());
            Path temp = Files.createTempFile(FILE.getParent(), "naturality-features-", ".tmp");
            try {
                Files.writeString(temp, GSON.toJson(this) + System.lineSeparator());
                Files.move(temp, FILE, StandardCopyOption.REPLACE_EXISTING);
            } finally { Files.deleteIfExists(temp); }
        } catch (Exception error) {
            LoggerFactory.getLogger("naturality").error("Could not save feature config", error);
        }
    }
}
