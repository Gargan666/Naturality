package naturality.client.systems.skyevents.framework;
import naturality.systems.skyevents.framework.SkyEvents.SkyEventPayload;
import naturality.systems.skyevents.framework.SkyEvents.SkyEventType;

import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.client.networking.v1.*;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;

/** One receiver for all dimension/event channels. All access is on the client thread. */
@org.jspecify.annotations.NullMarked
public final class SkyEventsClient {
    private record Key(Identifier dimension, String event) {}
    private static final Map<Key, Float> VALUES = new HashMap<>();
    private static final Map<Key, Long> APPEARANCES = new HashMap<>();
    public static long appearance(Level level, SkyEventType event) {
        return APPEARANCES.getOrDefault(new Key(level.dimension().identifier(), event.id), 0L);
    }
    private SkyEventsClient() {}
    public static void initialize() {
        ClientPlayConnectionEvents.INIT.register((_, _) -> { VALUES.clear(); APPEARANCES.clear(); });
        ClientPlayConnectionEvents.DISCONNECT.register((_, _) -> { VALUES.clear(); APPEARANCES.clear(); });
        ClientPlayNetworking.registerGlobalReceiver(SkyEventPayload.TYPE, (p, context) -> context.client().execute(() -> {
            if (Float.isFinite(p.strength())) VALUES.put(new Key(p.dimension(), p.event()), Math.clamp(p.strength(), 0, 20));
            APPEARANCES.put(new Key(p.dimension(), p.event()), p.appearance());
        }));
    }
    public static float strength(@org.jspecify.annotations.Nullable Level level, SkyEventType event) {
        if (!naturality.config.startup.FeatureConfig.Feature.SKY_EVENTS.enabled()) return 0;
        boolean visible = switch (event) {
            case PROXIMITY -> naturality.config.startup.FeatureConfig.Feature.PROXIMITY.enabled();
            case BLOOD_MOON -> naturality.config.startup.FeatureConfig.Feature.BLOOD_MOON.enabled();
            case METEOR_SHOWER -> naturality.config.startup.FeatureConfig.Feature.METEORS.enabled();
            case RAINBOW -> naturality.config.startup.FeatureConfig.Feature.RAINBOWS.enabled();
            case AURORA_BOREALIS, END_AURORA -> naturality.config.startup.FeatureConfig.Feature.AURORAS.enabled();
            case END_FLASHES -> naturality.config.startup.FeatureConfig.Feature.END_FLASHES.enabled();
        };
        if (!visible) return 0;
        return level == null ? 0 : VALUES.getOrDefault(new Key(level.dimension().identifier(), event.id), 0F);
    }
}
