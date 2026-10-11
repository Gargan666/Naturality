package naturality.client.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.WeakHashMap;
import naturality.entity.ArrowImpactPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Vector3f;

/** Impact and resolved model attachment lifetime is tied to the client entity. */
public final class MobArrowImpacts {
    private static final WeakHashMap<LivingEntity, List<Impact>> IMPACTS = new WeakHashMap<>();
    private MobArrowImpacts() {}
    public static void initialize() {
        ClientPlayNetworking.registerGlobalReceiver(ArrowImpactPayload.TYPE, (p, context) -> context.client().execute(() -> {
            if (!naturality.config.Feature.MOB_ARROWS.enabled()) return;
            var level = context.client().level;
            if (level == null || !level.dimension().identifier().equals(p.dimension()) || p.index() < 0) return;
            if (!(level.getEntity(p.entityId()) instanceof LivingEntity mob)) return;
            var hits = IMPACTS.computeIfAbsent(mob, _ -> new ArrayList<>());
            while (hits.size() <= p.index()) hits.add(null);
            hits.set(p.index(), new Impact(p));
        }));
    }
    public static List<Impact> impacts(LivingEntity mob) {
        var hits = IMPACTS.get(mob);
        if (hits == null) return List.of();
        // The impact packet can precede vanilla's arrow-count metadata update.
        // Keep unused tail entries; new hits overwrite their count-indexed slots.
        return new ArrayList<>(hits);
    }
    public static final class Impact {
        public final ArrowImpactPayload hit;
        public String path;
        public int cubeIndex;
        public Vector3f point;
        public Vector3f direction;
        Impact(ArrowImpactPayload hit) { this.hit = hit; }
    }
}
