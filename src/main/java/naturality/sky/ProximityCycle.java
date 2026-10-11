package naturality.sky;

import java.util.Map;
import naturality.weather.SavedRandom;

/** A fixed planet choice for a multi-night episode; days hide rather than reroll it. */
public final class ProximityCycle {
    public static final double START_CHANCE = 1 - Math.pow(.9, 1.0 / 1200);
    public static final String[] PLANETS = {"mercury", "venus", "mars", "jupiter", "saturn", "uranus", "neptune"};
    private final SavedRandom random;
    private int remaining, planet;
    private long appearance;
    public ProximityCycle(long seed) { random = new SavedRandom(seed); }
    public int strength() { return remaining > 0 ? planet : 0; }
    public int remaining() { return remaining; }
    public long appearance() { return appearance; }
    public void tick(boolean advance, boolean night) {
        if (!advance) return;
        if (remaining > 0) { if (--remaining == 0) planet = 0; }
        else if (night && random.nextDouble() < START_CHANCE) {
            planet = 1 + random.nextInt(7); appearance = random.nextLong();
            remaining = 48000 + random.nextInt(72001);
        }
    }
    public Map<String, Long> snapshot() {
        return Map.of("random", random.state(), "remaining", (long)remaining,
            "planet", (long)planet, "appearance", appearance);
    }
    public void restore(Map<String, Long> saved) {
        if (saved.isEmpty()) return;
        random.restore(saved.getOrDefault("random", random.state()));
        remaining = Math.max(0, saved.getOrDefault("remaining", 0L).intValue());
        planet = Math.clamp(saved.getOrDefault("planet", 0L).intValue(), 0, 7);
        appearance = saved.getOrDefault("appearance", 0L);
    }
}
