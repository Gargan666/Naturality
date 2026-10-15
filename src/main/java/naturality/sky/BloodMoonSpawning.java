package naturality.sky;

import naturality.config.Feature;
import net.minecraft.server.level.ServerLevel;

public final class BloodMoonSpawning {
    public interface State {
        void naturality$spawnMultiplier(int multiplier);
    }
    // The local calculator's nested counts do not retain their world; scope its cap lookup.
    public static final ThreadLocal<Integer> LOCAL_MULTIPLIER = ThreadLocal.withInitial(() -> 1);
    public static int multiplier(ServerLevel level) {
        return Feature.SKY_EVENT_CYCLES.enabled() && SkyEvents.strength(level, SkyEventType.BLOOD_MOON) > 0 ? 2 : 1;
    }
    private BloodMoonSpawning() {}
}
