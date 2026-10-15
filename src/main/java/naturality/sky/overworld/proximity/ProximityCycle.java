package naturality.sky.overworld.proximity;

import java.util.Map;
import naturality.api.random.SavedRandom;

/** A fixed planet choice for a multi-night episode; days hide rather than reroll it. */
@org.jspecify.annotations.NullMarked
public final class ProximityCycle {
    public static final double START_CHANCE = .05;
    public static final String[] PLANETS = {"mercury", "venus", "mars", "jupiter", "saturn", "uranus", "neptune"};
    private final SavedRandom random;
    private int remaining, planet;
    private long appearance;
    private boolean initialized, wasNight;
    public ProximityCycle(long seed) { random = new SavedRandom(seed); }
    public int strength() { return remaining > 0 ? planet : 0; }
    public int remaining() { return remaining; }
    public long appearance() { return appearance; }
    public void tick(boolean advance, boolean night) {
        tick(advance, night, 1);
    }
    public void tick(boolean advance, boolean night, int startMultiplier) {
        boolean nightStarted = initialized && night && !wasNight;
        initialized = true;
        wasNight = night;
        if (!advance) return;
        if (remaining > 0) { if (--remaining == 0) planet = 0; }
        else if (nightStarted && random.nextDouble() < naturality.api.worlds.PreviewEnvironment.chance(START_CHANCE, startMultiplier)) {
            planet = 1 + random.nextInt(7); appearance = random.nextLong();
            remaining = 48000 + random.nextInt(72001);
        }
    }
    public Map<String, Long> snapshot() {
        return Map.of("random", random.state(), "remaining", (long)remaining,
            "planet", (long)planet, "appearance", appearance,
            "initialized", initialized ? 1L : 0L, "wasNight", wasNight ? 1L : 0L);
    }
    public void restore(Map<String, Long> saved) {
        if (saved.isEmpty()) return;
        random.restore(saved.getOrDefault("random", random.state()));
        remaining = Math.max(0, saved.getOrDefault("remaining", 0L).intValue());
        planet = Math.clamp(saved.getOrDefault("planet", 0L).intValue(), 0, 7);
        appearance = saved.getOrDefault("appearance", 0L);
        initialized = saved.getOrDefault("initialized", 0L) != 0;
        wasNight = saved.getOrDefault("wasNight", 0L) != 0;
    }
}
