package naturality.sky;

import java.util.Random;

/** Night-only random start roll and weather-like active clock, independent of vanilla rain. */
public final class SkyEventCycle {
    public static final double START_CHANCE_PER_TICK = 1 - Math.pow(.9, 1.0 / 1200);
    private final Random random;
    private int remaining, retarget;
    private boolean active;
    private float strength, target;
    public SkyEventCycle(long seed) { random = new Random(seed); }
    public float strength() { return strength; }
    public boolean active() { return active; }
    public int remaining() { return remaining; }
    public void tick(boolean advance) {
        tick(advance, true);
    }
    /** A quiet event only rolls to start when its dimension permits starts. */
    public void tick(boolean advance, boolean canStart) {
        if (!advance) return;
        if (active && --remaining <= 0) {
            active = false;
            remaining = 0;
            retarget = 0;
        } else if (!active && canStart && random.nextDouble() < START_CHANCE_PER_TICK) {
            active = true;
            remaining = 12000 + random.nextInt(12001);
            retarget = 0;
        }
        if (active && --retarget <= 0) {
            target = 1 + random.nextFloat() * 19;
            retarget = 100 + random.nextInt(501);
        }
        if (!active) target = 0;
        // Exponential easing, snapped only when indistinguishably close.
        strength += (target - strength) * .005F;
        if (Math.abs(target - strength) < .001F) strength = target;
    }
}
