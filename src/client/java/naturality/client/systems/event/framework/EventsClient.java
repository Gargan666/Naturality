package naturality.client.systems.event.framework;

import java.util.HashMap;
import java.util.Map;
import naturality.systems.event.framework.Events.EventPayload;
import naturality.systems.event.framework.Events.EventType;
import net.fabricmc.fabric.api.client.networking.v1.*;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;

/** Synchronized gameplay event state; optional visual consumers apply their own gates. */
@org.jspecify.annotations.NullMarked
public final class EventsClient {
    private record Key(Identifier dimension, String event) {}
    private static final Map<Key, Float> VALUES = new HashMap<>();
    private EventsClient() {}
    public static void initialize() {
        ClientPlayConnectionEvents.INIT.register((_, _) -> VALUES.clear());
        ClientPlayConnectionEvents.DISCONNECT.register((_, _) -> VALUES.clear());
        if (naturality.config.startup.FeatureConfig.clientOnly()) return;
        ClientPlayNetworking.registerGlobalReceiver(EventPayload.TYPE, (payload, context) -> context.client().execute(() -> {
            if (Float.isFinite(payload.strength()))
                VALUES.put(new Key(payload.dimension(), payload.event()), Math.clamp(payload.strength(), 0, 1));
        }));
    }
    public static float strength(@org.jspecify.annotations.Nullable Level level, EventType event) {
        return level == null ? 0 : VALUES.getOrDefault(new Key(level.dimension().identifier(), event.id), 0F);
    }
}
