package naturality.client.config.gameplay;

import naturality.config.gameplay.GameplaySettings;
import naturality.config.gameplay.GameplaySettings.GameplaySettingsPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

@org.jspecify.annotations.NullMarked
public final class GameplaySettingsClient {
    private GameplaySettingsClient() {}
    public static void initialize() {
        // Until the server advertises its settings, use vanilla gameplay (also for unmodded servers).
        ClientPlayConnectionEvents.INIT.register((_, _) ->
            GameplaySettings.setClientSettings(new GameplaySettingsPayload(false, true, false, false, false, false, false, 200, 30)));
        ClientPlayConnectionEvents.DISCONNECT.register((_, _) -> GameplaySettings.setClientSettings(null));
        if (naturality.config.startup.FeatureConfig.clientOnly()) return;
        ClientPlayNetworking.registerGlobalReceiver(GameplaySettingsPayload.TYPE, (settings, context) -> {
            boolean changed = GameplaySettings.clientFireWrapping() != settings.fireWrapping()
                || GameplaySettings.clientSnowWrapping() != settings.snowWrapping();
            GameplaySettings.setClientSettings(settings);
            var client = context.client();
            var level = client.level;
            if (changed && level != null)
                client.levelRenderer.invalidateCompiledGeometry(level, client.options,
                    client.gameRenderer.mainCamera(), client.getBlockColors());
        });
    }
}
