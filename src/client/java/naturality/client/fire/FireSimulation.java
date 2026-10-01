package naturality.client.fire;

import java.util.SplittableRandom;

/** Fuel ignites at the base, then diffuses upward and cools; no looping sprite frames. */
public final class FireSimulation {
    public static final int WIDTH = 256, HEIGHT = 32;
    private final SplittableRandom random;
    private float[] heat = new float[WIDTH * HEIGHT];
    private float[] next = new float[WIDTH * HEIGHT];
    private final float[] fuel = new float[WIDTH];
    private final float[] target = new float[WIDTH];
    private long ticks;

    public FireSimulation(long seed) {
        random = new SplittableRandom(seed);
    }

    public void tick() {
        for (int x = 0; x < WIDTH; x += 3) {
            if (ticks == 0 || random.nextDouble() < 0.045) {
                float value = (float) (0.25 + random.nextDouble() * 1.35);
                for (int dx = 0; dx < 3 && x + dx < WIDTH; dx++) target[x + dx] = value;
            }
        }
        for (int x = 0; x < WIDTH; x++) fuel[x] += (target[x] - fuel[x]) * 0.35F;
        for (int y = 0; y < HEIGHT; y++) for (int x = 0; x < WIDTH; x++) {
            if (y == 0) {
                next[x] = fuel[x];
            } else {
                // Mostly advect, with slight sideways diffusion and wandering wind.
                // Heavy averaging would erase the tongues into a solid gradient.
                int drift = random.nextDouble() < 0.12 ? random.nextInt(-1, 2) : 0;
                float sum = sample(x + drift, y - 1) * 0.96F
                    + (sample(x + drift - 1, y - 1) + sample(x + drift + 1, y - 1)) * 0.02F;
                next[y * WIDTH + x] = Math.max(0, sum - (float) (0.018 + random.nextDouble() * 0.025));
            }
        }
        float[] old = heat; heat = next; next = old;
        ticks++;
    }

    public float sample(int x, int y) {
        return heat[Math.clamp(y, 0, HEIGHT - 1) * WIDTH + Math.floorMod(x, WIDTH)];
    }
    public long ticks() { return ticks; }
}
