package naturality.sky;

import naturality.weather.SavedRandom;

/** Daytime rain-only sky event with a probability peak at rain strength ten. */
public final class RainbowCycle {
    private static final double PEAK_TICK_CHANCE = 1 - Math.pow(.7, 1.0 / 1200);
    private final SavedRandom random;
    private int remaining;
    private boolean active;
    private float strength, target;
    public RainbowCycle(long seed) { random = new SavedRandom(seed); }
    public java.util.Map<String,Long> snapshot() {
        return java.util.Map.of("random",random.state(),"remaining",(long)remaining,"active",active?1L:0L,"strength",naturality.weather.EnvironmentWorldData.bits(strength),"target",naturality.weather.EnvironmentWorldData.bits(target));
    }
    public void restore(java.util.Map<String,Long> data) {
        if(data.isEmpty())return;
        random.restore(data.getOrDefault("random",random.state()));
        remaining=data.getOrDefault("remaining",0L).intValue();
        active=data.getOrDefault("active",0L)!=0;
        strength=naturality.weather.EnvironmentWorldData.number(data,"strength");
        target=naturality.weather.EnvironmentWorldData.number(data,"target");
    }
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
