package naturality.sky;

import java.util.Random;

/** Rare local events at any time of day; climate changes the chance of starting. */
public final class AuroraCycle {
    private final Random random;
    private int nextAttempt, remaining, retarget;
    private float strength, target;
    public AuroraCycle(long seed) { random = new Random(seed); nextAttempt = 200 + random.nextInt(401); }
    public float strength() { return strength; }
    public boolean active() { return remaining > 0; }
    public int nextAttempt() { return nextAttempt; }
    public static float startChance(float biomeTemperature, boolean snowy, float weatherTemperature) {
        float biomeCold = Math.clamp((.8F - biomeTemperature) / 1.3F + (snowy ? .35F : 0), 0, 1);
        // At weather temperature 0 in a mild plains biome, two 600-tick attempts
        // have approximately a 15% combined chance of starting an aurora.
        float weatherBoost = (float)Math.pow(2, (50 - Math.clamp(weatherTemperature, 0, 100)) / 8.77F);
        return .0015F * (1 + 8 * biomeCold) * weatherBoost;
    }
    public void tick(boolean advance, float chance) {
        if (!advance) return;
        if (remaining > 0) {
            remaining--;
            if (--retarget <= 0) { target = 4 + random.nextFloat() * 16; retarget = 200 + random.nextInt(401); }
            if (remaining == 0) { target = 0; nextAttempt = 2400; }
        } else if (--nextAttempt <= 0) {
            nextAttempt = 600;
            if (random.nextFloat() < Math.clamp(chance, 0, 1)) {
                remaining = 2400 + random.nextInt(4801);
                retarget = 0;
            }
        }
        strength += (target - strength) * .005F;
        if (Math.abs(target - strength) < .001F) strength = target;
    }
}
