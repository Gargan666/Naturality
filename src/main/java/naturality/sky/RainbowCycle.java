package naturality.sky;

import java.util.Random;

/** Daytime rain-only sky event with a probability peak at rain strength ten. */
public final class RainbowCycle {
    private static final double PEAK_TICK_CHANCE = 1 - Math.pow(.7, 1.0 / 1200);
    private final Random random;
    private int remaining;
    private boolean active;
    private float strength, target;
    public RainbowCycle(long seed) { random = new Random(seed); }
    public boolean active() { return active; }
    public float strength() { return strength; }
    public int remaining() { return remaining; }
    public static boolean validRain(float rain) { return rain > 1 && rain < 20; }
    public static double startChance(float rain) {
        if (!validRain(rain)) return 0;
        double t = rain <= 10 ? (rain - 1) / 9.0 : (20 - rain) / 10.0;
        t = Math.clamp(t, 0, 1);
        return PEAK_TICK_CHANCE * t * t * (3 - 2 * t);
    }
    public void tick(boolean advance, boolean daylight, float rain) {
        if (!advance) return;
        if (!daylight || !validRain(rain)) {
            active = false;
            remaining = 0;
            target = 0;
        } else if (active) {
            if (--remaining <= 0) {
                active = false;
                remaining = 0;
                target = 0;
            }
        } else if (random.nextDouble() < startChance(rain)) {
            active = true;
            remaining = 1200 + random.nextInt(2401);
            // Keep one chosen size for this rainbow's entire lifetime.
            target = 5 + random.nextFloat() * 15;
        }
        strength += (target - strength) * .005F;
        if (Math.abs(target - strength) < .001F) strength = target;
    }
}
