package naturality.client.api.ropes;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

/** Client-side Verlet rope: pinned ends, gravity, inertia and length constraints. */
public final class RopeChain {
    static final int SEGMENTS = 24;
    private static final Map<Object, RopeChain> CHAINS = new HashMap<>();
    private static long lastPrune;
    final RopeSimulation simulation = new RopeSimulation();
    private boolean initialized;
    private long lastTime, lastSeen;
    private double remainder, length;
    private Object level;

    public static Vec3[] sample(Object key, Vec3 start, Vec3 end) {
        return sampleChain(key, start, end).simulation.snapshot();
    }

    static RopeChain sampleChain(Object key, Vec3 start, Vec3 end) {
        long now = System.nanoTime();
        if (now - lastPrune > 1_000_000_000L) {
            CHAINS.entrySet().removeIf(entry -> now - entry.getValue().lastSeen > 5_000_000_000L);
            lastPrune = now;
        }
        RopeChain chain = CHAINS.computeIfAbsent(key, _ -> new RopeChain());
        chain.update(start, end, now);
        return chain;
    }

    private void update(Vec3 start, Vec3 end, long now) {
        var client = Minecraft.getInstance();
        double desired = start.distanceTo(end) * 1.06 + .12;
        if (!initialized || level != client.level || simulation.distanceSquared(0, start) > 64
                || simulation.distanceSquared(SEGMENTS, end) > 64) {
            initialized = true;
            level = client.level;
            length = desired;
            simulation.reset(start, end);
            lastTime = now;
            remainder = 0;
        }
        double elapsed = Math.min(.1, (now - lastTime) / 1_000_000_000.0);
        lastTime = lastSeen = now;
        if (!client.isPaused()) remainder += elapsed * naturality.client.systems.sleep.SleepVisuals.speed();
        length = Math.max(desired, length * .98 + desired * .02);
        simulation.pin(start, end);
        while (remainder >= .025) {
            remainder -= .025;
            simulation.step(start, end, length);
        }
        simulation.pin(start, end);
    }
    private RopeChain() { }

    /** Allocation-free Verlet solver; the render thread owns each instance. */
    static final class RopeSimulation {
        static final int SEGMENTS = 24;
        final double[] x = new double[25], y = new double[25], z = new double[25];
        private final double[] px = new double[25], py = new double[25], pz = new double[25];

        void reset(Vec3 start, Vec3 end) {
            for (int i = 0; i <= SEGMENTS; i++) {
                double t = i / (double) SEGMENTS;
                x[i] = px[i] = start.x + t * (end.x - start.x);
                y[i] = py[i] = start.y + t * (end.y - start.y) - .15 * Math.sin(t * Math.PI);
                z[i] = pz[i] = start.z + t * (end.z - start.z);
            }
        }

        void pin(Vec3 start, Vec3 end) {
            x[0] = px[0] = start.x; y[0] = py[0] = start.y; z[0] = pz[0] = start.z;
            x[SEGMENTS] = px[SEGMENTS] = end.x;
            y[SEGMENTS] = py[SEGMENTS] = end.y;
            z[SEGMENTS] = pz[SEGMENTS] = end.z;
        }

        void step(Vec3 start, Vec3 end, double length) {
            for (int i = 1; i < SEGMENTS; i++) {
                double cx = x[i], cy = y[i], cz = z[i];
                x[i] = cx + (cx - px[i]) * .97;
                y[i] = cy + (cy - py[i]) * .97 - .009;
                z[i] = cz + (cz - pz[i]) * .97;
                px[i] = cx; py[i] = cy; pz[i] = cz;
            }
            double segmentLength = length / SEGMENTS;
            for (int pass = 0; pass < 16; pass++) {
                pin(start, end);
                for (int i = 0; i < SEGMENTS; i++) {
                    int j = i + 1;
                    double dx = x[j] - x[i], dy = y[j] - y[i], dz = z[j] - z[i];
                    double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
                    if (distance < 1.0e-8) continue;
                    double scale = (distance - segmentLength) / distance;
                    dx *= scale; dy *= scale; dz *= scale;
                    if (i == 0) { x[j] -= dx; y[j] -= dy; z[j] -= dz; }
                    else if (j == SEGMENTS) { x[i] += dx; y[i] += dy; z[i] += dz; }
                    else {
                        dx *= .5; dy *= .5; dz *= .5;
                        x[i] += dx; y[i] += dy; z[i] += dz;
                        x[j] -= dx; y[j] -= dy; z[j] -= dz;
                    }
                }
            }
            pin(start, end);
        }

        double distanceSquared(int i, Vec3 point) {
            double dx = x[i] - point.x, dy = y[i] - point.y, dz = z[i] - point.z;
            return dx * dx + dy * dy + dz * dz;
        }

        Vec3[] snapshot() {
            Vec3[] result = new Vec3[25];
            for (int i = 0; i <= SEGMENTS; i++) result[i] = new Vec3(x[i], y[i], z[i]);
            return result;
        }
    }
}
