package naturality.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.LoggerFactory;

/** Owned by the server installation; clients cannot change a remote server's gameplay. */
public final class NaturalityServerConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("naturality-server.json");
    private static volatile java.util.Map<String, naturality.weather.WeatherProfile> legacyWeather = java.util.Map.of();
    private static final NaturalityServerConfig INSTANCE = load();
    public volatile boolean portalIntroAnimation = true;
    public volatile boolean fireWrapping = true;
    public volatile boolean snowWrapping = true;
    public volatile boolean snowCompaction = true;
    public volatile boolean weatherThaw = true;
    public volatile boolean weatherSnowAccumulation = true;
    public volatile boolean vanillaPortalEntry = false;
    public volatile java.util.Map<String, java.util.Map<String, naturality.sky.SkyEventSettings>> skyEvents = naturality.sky.SkyEvents.defaults();

    @SuppressWarnings({"null", "unused"}) // Validate values populated reflectively by Gson.

    public void validate() {
        var skyDefaults = naturality.sky.SkyEvents.defaults();
        if (skyEvents != null) skyDefaults.forEach((dimension, events) -> {
            var saved = skyEvents.get(dimension);
            if (saved != null) events.replaceAll((id, fallback) -> {
                var value = saved.get(id); if (value == null) return fallback; value.validate(); return value;
            });
        });
        skyEvents = skyDefaults;
    }

    public static java.util.Map<String, naturality.weather.WeatherProfile> takeLegacyWeather() {
        var result = legacyWeather;
        legacyWeather = java.util.Map.of();
        return result;
    }

    public static NaturalityServerConfig get() { return INSTANCE; }

    private static NaturalityServerConfig load() {
        if (Files.exists(FILE)) {
            try (var reader = Files.newBufferedReader(FILE)) {
                var root = JsonParser.parseReader(reader).getAsJsonObject();
                var legacy = root.remove("weather");
                if (legacy != null && legacy.isJsonObject()) {
                    var type = new com.google.gson.reflect.TypeToken<java.util.Map<String, naturality.weather.WeatherProfile>>() {}.getType();
                    java.util.Map<String, naturality.weather.WeatherProfile> profiles = GSON.fromJson(legacy, type);
                    if (profiles != null) legacyWeather = profiles;
                }
                NaturalityServerConfig config = GSON.fromJson(root, NaturalityServerConfig.class);
                if (config != null) { config.validate(); return config; }
            } catch (Exception e) {
                LoggerFactory.getLogger("naturality").error("Could not read server config; using defaults", e);
            }
            return new NaturalityServerConfig();
        }
        NaturalityServerConfig config = new NaturalityServerConfig();
        config.save();
        return config;
    }

    @SuppressWarnings({"null", "unused"}) // Validate values populated reflectively by Gson.

    public void save() {
        validate();
        try {
            Files.createDirectories(FILE.getParent());
            Path temp = Files.createTempFile(FILE.getParent(), "naturality-server-", ".tmp");
            try {
                Files.writeString(temp, GSON.toJson(this) + System.lineSeparator());
                Files.move(temp, FILE, StandardCopyOption.REPLACE_EXISTING);
            } finally { Files.deleteIfExists(temp); }
        } catch (Exception e) {
            LoggerFactory.getLogger("naturality").error("Could not save server config", e);
        }
    }
}

