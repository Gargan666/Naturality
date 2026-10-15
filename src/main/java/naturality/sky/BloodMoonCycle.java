package naturality.sky;

import java.util.Map;
import naturality.weather.SavedRandom;

/** A single nightfall roll; a successful Blood Moon ends when that night ends. */
public final class BloodMoonCycle {
    public static final double START_CHANCE = .05;
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
        else if (started) active = random.nextDouble() < naturality.weather.PreviewEnvironment.chance(START_CHANCE, startMultiplier);
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
}
