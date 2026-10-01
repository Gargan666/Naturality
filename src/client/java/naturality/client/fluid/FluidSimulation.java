package naturality.client.fluid;

import java.util.SplittableRandom;

/** Live three-layer cellular automata, extended to a larger toroidal domain.
 * Equations: https://github.com/ClassiCube/ClassiCube/wiki/Minecraft-Classic-lava-animation-algorithm
 * Flowing variants use longitudinal stencils and advect the displayed field.
 */
public final class FluidSimulation {
    public static final int SIZE = 64;
    private static final int[] DISPLACEMENT = new int[16];
    static {
        for (int i = 0; i < DISPLACEMENT.length; i++)
            DISPLACEMENT[i] = (int) (1.2 * Math.sin(i * Math.PI / 8));
    }
    private final boolean lava;
    private final boolean flowing;
    private final SplittableRandom random;
    private float[] surface = new float[SIZE * SIZE];
    private float[] next = new float[SIZE * SIZE];
    private final float[] reservoir = new float[SIZE * SIZE];
    private final float[] impulse = new float[SIZE * SIZE];
    private long ticks;

    public FluidSimulation(boolean lava, boolean flowing, long seed) {
        this.lava = lava;
        this.flowing = flowing;
        random = new SplittableRandom(seed);
    }

    private static int index(int x, int y) {
        return (y & (SIZE - 1)) * SIZE + (x & (SIZE - 1));
    }

    public void tick() {
        for (int y = 0; y < SIZE; y++) for (int x = 0; x < SIZE; x++) {
            int i = index(x, y);
            float sum = 0;
            if (lava && flowing) {
                // Upstream-biased transport stretches hot strands along +V, with
                // only weak cross-stream diffusion. Distinct from still lava's swirl.
                next[i] = surface[i] * 0.24F
                    + surface[index(x, y - 1)] * 0.16F
                    + surface[index(x, y - 2)] * 0.24F
                    + surface[index(x, y - 3)] * 0.16F
                    + surface[index(x, y + 1)] * 0.04F
                    + (surface[index(x - 1, y - 1)] + surface[index(x + 1, y - 1)]) * 0.02F
                    + (reservoir[i] + reservoir[index(x + 1, y)]
                        + reservoir[index(x, y + 1)] + reservoir[index(x + 1, y + 1)]) * 0.24F;
            } else if (lava) {
                int cx = x + DISPLACEMENT[y & 15];
                int cy = y + DISPLACEMENT[x & 15];
                for (int dy = -1; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++)
                    sum += surface[index(cx + dx, cy + dy)];
                float local = reservoir[i] + reservoir[index(x + 1, y)]
                    + reservoir[index(x, y + 1)] + reservoir[index(x + 1, y + 1)];
                // Shorter diffusion memory keeps individual cells compact. Raise
                // the local heat contribution to retain the original mean heat.
                next[i] = sum / 11 + local * (4F / 11);
            } else if (flowing) {
                // V is the flow axis in vanilla fluid UVs. Smoothing along it
                // makes longitudinal streaks instead of transverse falling bars.
                for (int dy = -2; dy <= 2; dy++) sum += surface[index(x, y + dy)];
                next[i] = sum / 5.5F + reservoir[i] * 0.8F;
            } else {
                for (int dx = -1; dx <= 1; dx++) sum += surface[index(x + dx, y)];
                next[i] = sum / 3.3F + reservoir[i] * 0.8F;
            }
        }
        // Separate passes keep the stencil independent of traversal direction.
        for (int i = 0; i < surface.length; i++) {
            reservoir[i] = Math.max(0, reservoir[i] + impulse[i] * (lava ? 0.01F : 0.05F));
            impulse[i] -= lava ? 0.06F : 0.1F;
            if (random.nextDouble() < (lava ? 0.005 : 0.05)) impulse[i] = lava ? 1.5F : 0.5F;
        }
        float[] previous = surface;
        surface = next;
        next = previous;
        ticks++;
    }

    public float heat(int x, int y) {
        int scroll = flowing ? (int) ((lava ? ticks / 2 : ticks) & (SIZE - 1)) : 0;
        float value = surface[index(x, y - scroll)];
        // Preserve variation in hot regions instead of clipping them into connected
        // white/yellow plateaus. This drives the active lava sprite's color palette.
        return lava ? value / (value + 0.35F) : Math.clamp(value, 0, 1);
    }

    public long ticks() { return ticks; }
}
