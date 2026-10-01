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
    private SkyEventsClient() {}
    public static void initialize() {
        ClientPlayConnectionEvents.INIT.register((_, _) -> VALUES.clear());
        ClientPlayConnectionEvents.DISCONNECT.register((_, _) -> VALUES.clear());
        ClientPlayNetworking.registerGlobalReceiver(SkyEventPayload.TYPE, (p, context) -> context.client().execute(() -> {
            if (Float.isFinite(p.strength())) VALUES.put(new Key(p.dimension(), p.event()), Math.clamp(p.strength(), 0, 20));
        }));
    }
    public static float strength(@org.jspecify.annotations.Nullable Level level, SkyEventType event) {
        return level == null ? 0 : VALUES.getOrDefault(new Key(level.dimension().identifier(), event.id), 0F);
    }
}
