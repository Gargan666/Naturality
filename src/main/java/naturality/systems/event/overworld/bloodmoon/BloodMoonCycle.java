package naturality.systems.event.overworld.bloodmoon;
import naturality.systems.event.framework.Events.EventType;
import naturality.systems.event.framework.Events;

import java.util.Map;
import naturality.api.random.SavedRandom;
import naturality.config.startup.FeatureConfig.Feature;
import net.minecraft.server.level.ServerLevel;

/** A single nightfall roll; a successful Blood Moon ends when that night ends. */
@org.jspecify.annotations.NullMarked
public final class BloodMoonCycle implements Events.Cycle {
    @Override public void tick(ServerLevel level, int startMultiplier) {
        long time = Math.floorMod(level.getOverworldClockTime(), 24000L);
        tick(level.getGameRules().get(net.minecraft.world.level.gamerules.GameRules.ADVANCE_WEATHER),
            time >= 13000 && time < 23000, startMultiplier);
    }
    public static final double START_CHANCE = .01;
    private final SavedRandom random;
    private boolean initialized, wasNight, active;
    public BloodMoonCycle(long seed) { random = new SavedRandom(seed); }
    public int strength() { return active ? 1 : 0; }
    public void tick(boolean advance, boolean night) {
        tick(advance, night, 1);
    }
    public void tick(boolean advance, boolean night, int startMultiplier) {
        boolean started = initialized && night && !wasNight;
        initialized = true; wasNight = night;
        if (!advance) return;
        if (!night) active = false;
        else if (started) active = random.nextDouble() < naturality.api.worlds.PreviewEnvironment.chance(START_CHANCE, startMultiplier);
    }
    public Map<String, Long> snapshot() {
        return Map.of("random", random.state(), "initialized", initialized ? 1L : 0L,
            "wasNight", wasNight ? 1L : 0L, "active", active ? 1L : 0L);
    }
    public void restore(Map<String, Long> saved) {
        if (saved.isEmpty()) return;
        random.restore(saved.getOrDefault("random", random.state()));
        initialized = saved.getOrDefault("initialized", 0L) != 0;
        wasNight = saved.getOrDefault("wasNight", 0L) != 0;
        active = saved.getOrDefault("active", 0L) != 0;
    }

    public static final class BloodMoonSpawning {
        public interface State {
            void naturality$spawnMultiplier(int multiplier);
        }
        // The local calculator's nested counts do not retain their world; scope its cap lookup.
        public static final ThreadLocal<Integer> LOCAL_MULTIPLIER = ThreadLocal.withInitial(() -> 1);
        public static int multiplier(ServerLevel level) {
            return Feature.EVENT_CYCLES.enabled() && Events.strength(level, EventType.BLOOD_MOON) > 0 ? 2 : 1;
        }
        private BloodMoonSpawning() {}
    }
}
