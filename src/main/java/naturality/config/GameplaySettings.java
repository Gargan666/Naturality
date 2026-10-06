package naturality.config;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;

/** Server authority plus a client snapshot; no client packet can change server settings. */
public final class GameplaySettings {
    private static volatile @org.jspecify.annotations.Nullable GameplaySettingsPayload clientSettings;
    private GameplaySettings() {}

    public static GameplaySettingsPayload serverSettings() {
        var config = NaturalityServerConfig.get();
        return new GameplaySettingsPayload(config.fireWrapping, config.vanillaPortalEntry, config.snowWrapping, config.snowCompaction, config.weatherThaw, config.weatherSnowAccumulation);
    }

    public static void setClientSettings(@org.jspecify.annotations.Nullable GameplaySettingsPayload settings) { clientSettings = settings; }
    public static boolean clientFireWrapping() {
        var settings = clientSettings;
        return settings == null ? NaturalityServerConfig.get().fireWrapping : settings.fireWrapping();
    }
    public static boolean clientPhysicalPortalEntry() {
        var settings = clientSettings;
        return !(settings == null ? NaturalityServerConfig.get().vanillaPortalEntry : settings.vanillaPortalEntry());
    }
    public static boolean fireWrapping(BlockGetter level) {
        if (level instanceof Level world && !world.isClientSide()) return NaturalityServerConfig.get().fireWrapping;
        return clientFireWrapping();
    }
    public static boolean clientSnowWrapping() {
        var settings = clientSettings;
        return settings == null ? NaturalityServerConfig.get().snowWrapping : settings.snowWrapping();
    }
    public static boolean clientSnowCompaction() {
        var settings = clientSettings;
        return settings == null ? NaturalityServerConfig.get().snowCompaction : settings.snowCompaction();
    }
    public static boolean clientWeatherThaw() {
        var settings = clientSettings;
        return settings == null ? NaturalityServerConfig.get().weatherThaw : settings.weatherThaw();
    }
    public static boolean clientWeatherSnowAccumulation() {
        var settings = clientSettings;
        return settings == null ? NaturalityServerConfig.get().weatherSnowAccumulation : settings.weatherSnowAccumulation();
    }
    public static boolean snowWrapping(BlockGetter level) {
        // Static block-state caches must retain vanilla shapes for live OFF transitions.
        if (level == net.minecraft.world.level.EmptyBlockGetter.INSTANCE) return false;
        if (level instanceof net.minecraft.world.level.WorldGenLevel) return NaturalityServerConfig.get().snowWrapping;
        if (level instanceof Level world && !world.isClientSide()) return NaturalityServerConfig.get().snowWrapping;
        return clientSnowWrapping();
    }
    public static boolean physicalPortalEntry(Level level) {
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
}
