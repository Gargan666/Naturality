package naturality.client.villager;

import net.minecraft.world.phys.Vec3;

/** Allocation-free Verlet solver; the render thread owns each instance. */
final class RopeSimulation {
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
