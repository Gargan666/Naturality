package naturality.config.gameplay;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import naturality.Naturality;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.LoggerFactory;

/** Server authority plus a client snapshot; no client packet can change server settings. */
@org.jspecify.annotations.NullMarked
public final class GameplaySettings {
    private static volatile @org.jspecify.annotations.Nullable GameplaySettingsPayload clientSettings;
    private GameplaySettings() {}

    public static GameplaySettingsPayload serverSettings() {
        if (naturality.config.startup.FeatureConfig.clientOnly())
            return new GameplaySettingsPayload(false, true, false, false, false, false, false, 200, 30);
        var config = NaturalityServerConfig.get();
        return new GameplaySettingsPayload(config.fireWrapping, config.vanillaPortalEntry, config.snowWrapping, config.snowCompaction, config.weatherThaw, config.weatherSnowAccumulation, config.sleepAcceleration, config.sleepMaxSpeed, config.sleepRampSeconds);
    }

    public static GameplaySettingsPayload displayedSettings() {
        if (naturality.config.startup.FeatureConfig.clientOnly()) return serverSettings();
        return clientSettings == null ? serverSettings() : clientSettings;
    }
    public static void setClientSettings(@org.jspecify.annotations.Nullable GameplaySettingsPayload settings) { clientSettings = settings; }
    public static boolean clientFireWrapping() {
        if (naturality.config.startup.FeatureConfig.clientOnly()) return false;
        var settings = clientSettings;
        return settings == null ? NaturalityServerConfig.get().fireWrapping : settings.fireWrapping();
    }
    public static boolean clientPhysicalPortalEntry() {
        if (naturality.config.startup.FeatureConfig.clientOnly()) return false;
        var settings = clientSettings;
        return !(settings == null ? NaturalityServerConfig.get().vanillaPortalEntry : settings.vanillaPortalEntry());
    }
    public static boolean fireWrapping(BlockGetter level) {
        if (naturality.config.startup.FeatureConfig.clientOnly()) return false;
        if (level instanceof Level world && !world.isClientSide()) return NaturalityServerConfig.get().fireWrapping;
        return clientFireWrapping();
    }
    public static boolean clientSnowWrapping() {
        if (naturality.config.startup.FeatureConfig.clientOnly()) return false;
        var settings = clientSettings;
        return settings == null ? NaturalityServerConfig.get().snowWrapping : settings.snowWrapping();
    }
    public static boolean clientSnowCompaction() {
        if (naturality.config.startup.FeatureConfig.clientOnly()) return false;
        var settings = clientSettings;
        return settings == null ? NaturalityServerConfig.get().snowCompaction : settings.snowCompaction();
    }
    public static boolean clientWeatherThaw() {
        if (naturality.config.startup.FeatureConfig.clientOnly()) return false;
        var settings = clientSettings;
        return settings == null ? NaturalityServerConfig.get().weatherThaw : settings.weatherThaw();
    }
    public static boolean clientWeatherSnowAccumulation() {
        if (naturality.config.startup.FeatureConfig.clientOnly()) return false;
        var settings = clientSettings;
        return settings == null ? NaturalityServerConfig.get().weatherSnowAccumulation : settings.weatherSnowAccumulation();
    }
    public static boolean snowWrapping(BlockGetter level) {
        if (naturality.config.startup.FeatureConfig.clientOnly()) return false;
        // Static block-state caches must retain vanilla shapes for live OFF transitions.
        if (level == net.minecraft.world.level.EmptyBlockGetter.INSTANCE) return false;
        if (level instanceof net.minecraft.world.level.WorldGenLevel) return NaturalityServerConfig.get().snowWrapping;
        if (level instanceof Level world && !world.isClientSide()) return NaturalityServerConfig.get().snowWrapping;
        return clientSnowWrapping();
    }
    public static boolean physicalPortalEntry(Level level) {
        if (naturality.config.startup.FeatureConfig.clientOnly()) return false;
        return level.isClientSide() ? clientPhysicalPortalEntry() : !NaturalityServerConfig.get().vanillaPortalEntry;
    }

    public static void initialize() {
        PayloadTypeRegistry.clientboundPlay().register(GameplaySettingsPayload.TYPE, GameplaySettingsPayload.CODEC);
        ServerPlayConnectionEvents.JOIN.register((handler, _, _) -> {
            if (ServerPlayNetworking.canSend(handler.player, GameplaySettingsPayload.TYPE))
                ServerPlayNetworking.send(handler.player, serverSettings());
        });
        // Compare on the server thread so local-host edits also reach LAN players.
        var previous = new GameplaySettingsPayload[1];
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            var current = serverSettings();
            if (current.equals(previous[0])) return;
            previous[0] = current;
            for (var player : server.getPlayerList().getPlayers())
                if (ServerPlayNetworking.canSend(player, GameplaySettingsPayload.TYPE))
                    ServerPlayNetworking.send(player, current);
        });
    }

    public record GameplaySettingsPayload(boolean fireWrapping, boolean vanillaPortalEntry, boolean snowWrapping, boolean snowCompaction, boolean weatherThaw, boolean weatherSnowAccumulation, boolean sleepAcceleration, int sleepMaxSpeed, int sleepRampSeconds) implements CustomPacketPayload {
        public GameplaySettingsPayload(boolean fireWrapping, boolean vanillaPortalEntry, boolean snowWrapping, boolean snowCompaction, boolean weatherThaw, boolean weatherSnowAccumulation) {
            this(fireWrapping, vanillaPortalEntry, snowWrapping, snowCompaction, weatherThaw, weatherSnowAccumulation, true, 200, 30);
        }
        public GameplaySettingsPayload(boolean fireWrapping, boolean vanillaPortalEntry) {
            this(fireWrapping, vanillaPortalEntry, true, true, true, true);
        }
        public static final Type<GameplaySettingsPayload> TYPE = new Type<>(Naturality.id("gameplay_settings_v4"));
        public static final StreamCodec<RegistryFriendlyByteBuf, GameplaySettingsPayload> CODEC = new StreamCodec<@org.jspecify.annotations.NonNull RegistryFriendlyByteBuf, @org.jspecify.annotations.NonNull GameplaySettingsPayload>() {
            public GameplaySettingsPayload decode(RegistryFriendlyByteBuf buffer) {
                return new GameplaySettingsPayload(buffer.readBoolean(), buffer.readBoolean(), buffer.readBoolean(), buffer.readBoolean(), buffer.readBoolean(), buffer.readBoolean(), buffer.readBoolean(), buffer.readVarInt(), buffer.readVarInt());
            }
            public void encode(RegistryFriendlyByteBuf buffer, GameplaySettingsPayload settings) {
                buffer.writeBoolean(settings.fireWrapping());
                buffer.writeBoolean(settings.vanillaPortalEntry());
                buffer.writeBoolean(settings.snowWrapping());
                buffer.writeBoolean(settings.snowCompaction());
                buffer.writeBoolean(settings.weatherThaw());
                buffer.writeBoolean(settings.weatherSnowAccumulation());
                buffer.writeBoolean(settings.sleepAcceleration());
                buffer.writeVarInt(settings.sleepMaxSpeed());
                buffer.writeVarInt(settings.sleepRampSeconds());
            }
        };
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Owned by the server installation; clients cannot change a remote server's gameplay. */
    public static final class NaturalityServerConfig {
        private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
        private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("naturality-server.json");
        private static volatile java.util.Map<String, naturality.systems.weather.framework.WeatherSystem.WeatherProfile> legacyWeather = java.util.Map.of();
        private static final NaturalityServerConfig INSTANCE = load();
        public volatile boolean sleepAcceleration = true;
        public volatile int sleepMaxSpeed = 200;
        public volatile int sleepRampSeconds = 30;
        public volatile boolean portalIntroAnimation = true;
        public volatile boolean fireWrapping = true;
        public volatile boolean snowWrapping = true;
        public volatile boolean snowCompaction = true;
        public volatile boolean weatherThaw = true;
        public volatile boolean weatherSnowAccumulation = true;
        public volatile boolean vanillaPortalEntry = false;

        @SuppressWarnings({"null", "unused"}) // Validate values populated reflectively by Gson.

        public void validate() {
            sleepMaxSpeed = Math.clamp(sleepMaxSpeed, 1, 1000);
            sleepRampSeconds = Math.clamp(sleepRampSeconds, 1, 120);
        }

        public static java.util.Map<String, naturality.systems.weather.framework.WeatherSystem.WeatherProfile> takeLegacyWeather() {
            var result = legacyWeather;
            legacyWeather = java.util.Map.of();
            return result;
        }

        public static NaturalityServerConfig get() { return INSTANCE; }

        public void reset() {
            var defaults = new NaturalityServerConfig();
            sleepAcceleration = defaults.sleepAcceleration;
            sleepMaxSpeed = defaults.sleepMaxSpeed;
            sleepRampSeconds = defaults.sleepRampSeconds;
            portalIntroAnimation = defaults.portalIntroAnimation;
            fireWrapping = defaults.fireWrapping;
            snowWrapping = defaults.snowWrapping;
            snowCompaction = defaults.snowCompaction;
            weatherThaw = defaults.weatherThaw;
            weatherSnowAccumulation = defaults.weatherSnowAccumulation;
            vanillaPortalEntry = defaults.vanillaPortalEntry;
        }

        private static NaturalityServerConfig load() {
            if (Files.exists(FILE)) {
                try (var reader = Files.newBufferedReader(FILE)) {
                    var root = JsonParser.parseReader(reader).getAsJsonObject();
                    var legacy = root.remove("weather");
                    if (legacy != null && legacy.isJsonObject()) {
                        var type = new com.google.gson.reflect.TypeToken<java.util.Map<String, naturality.systems.weather.framework.WeatherSystem.WeatherProfile>>() {}.getType();
                        java.util.Map<String, naturality.systems.weather.framework.WeatherSystem.WeatherProfile> profiles = GSON.fromJson(legacy, type);
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
}
