package naturality.client.particle;

import java.util.ArrayDeque;
import naturality.particle.FireballImpactPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.multiplayer.ClientLevel;
import org.jspecify.annotations.Nullable;

/** Short-lived impact metadata paired with the immediately following explosion. */
public final class FireballImpactClient {
    private record Pending(FireballImpactPayload payload, long expires) {}
    private static final ArrayDeque<Pending> PENDING = new ArrayDeque<>();
    private static @Nullable ClientLevel world;
    private FireballImpactClient() {}

    public static void initialize() {
        ClientPlayNetworking.registerGlobalReceiver(FireballImpactPayload.TYPE, (payload, context) -> context.client().execute(() -> {
            if (!naturality.config.Feature.EXPLOSION_PARTICLES.enabled()) return;
            var level = context.client().level;
            if (level == null || !level.dimension().identifier().equals(payload.dimension())) return;
            sync(level);
            if (PENDING.size() >= 64) PENDING.removeFirst();
            PENDING.addLast(new Pending(payload, level.getGameTime() + 20));
        }));
    }

    private static void sync(ClientLevel level) {
        if (world != level) { PENDING.clear(); world = level; }
        PENDING.removeIf(pending -> pending.expires < level.getGameTime());
    }

    public static @Nullable FireballImpactPayload consume(ClientLevel level, double x, double y, double z) {
        sync(level);
        var iterator = PENDING.iterator();
        while (iterator.hasNext()) {
            var payload = iterator.next().payload;
            double dx = payload.x() - x, dy = payload.y() - y, dz = payload.z() - z;
            if (dx * dx + dy * dy + dz * dz < 0.000001) {
                iterator.remove();
                return payload;
            }
        }
        return null;
    }
}
