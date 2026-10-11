package naturality.client.sky;

import java.util.HashMap;
import java.util.Map;
import naturality.sky.*;
import net.fabricmc.fabric.api.client.networking.v1.*;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;

/** One receiver for all dimension/event channels. All access is on the client thread. */
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
        if (!naturality.config.Feature.SKY_EVENTS.enabled()) return 0;
        boolean visible = switch (event) {
            case PROXIMITY -> naturality.config.Feature.PROXIMITY.enabled();
            case METEOR_SHOWER -> naturality.config.Feature.METEORS.enabled();
            case RAINBOW -> naturality.config.Feature.RAINBOWS.enabled();
            case AURORA_BOREALIS, END_AURORA -> naturality.config.Feature.AURORAS.enabled();
            case END_FLASHES -> naturality.config.Feature.END_FLASHES.enabled();
        };
        if (!visible) return 0;
        return level == null ? 0 : VALUES.getOrDefault(new Key(level.dimension().identifier(), event.id), 0F);
    }
}
