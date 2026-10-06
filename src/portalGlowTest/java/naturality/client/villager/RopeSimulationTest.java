package naturality.client.villager;

import java.lang.management.ManagementFactory;
import net.minecraft.world.phys.Vec3;

public final class RopeSimulationTest {
    public static void run() {
        var scalar = new RopeSimulation();
        Vec3 start = new Vec3(7, 100, -3), end = new Vec3(11, 102, 2);
        Vec3[] points = new Vec3[25], previous = new Vec3[25];
        scalar.reset(start, end);
        for (int i = 0; i <= 24; i++)
            points[i] = previous[i] = start.lerp(end, i / 24.0).add(0, -.15 * Math.sin(i / 24.0 * Math.PI), 0);
        for (int tick = 0; tick < 400; tick++) {
            Vec3 moving = end.add(Math.sin(tick * .03), Math.cos(tick * .02) * .4, 0);
            double length = start.distanceTo(moving) * 1.06 + .12;
            scalar.pin(start, moving);
            scalar.step(start, moving, length);
            reference(points, previous, start, moving, length);
            for (int i = 0; i <= 24; i++) {
                if (scalar.distanceSquared(i, points[i]) > 1e-16)
                    throw new AssertionError("Rope diverged from original solver at step " + tick + ", point " + i);
            }
        }
        scalar.reset(start, start);
        for (int i = 0; i < 100; i++) scalar.step(start, start, .12);
        for (Vec3 p : scalar.snapshot()) if (!Double.isFinite(p.x + p.y + p.z))
            throw new AssertionError("Coincident endpoints must remain finite");

        var bean = (com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();
        bean.setThreadAllocatedMemoryEnabled(true);
        for (int i = 0; i < 2000; i++) scalar.step(start, end, 7);
        long thread = Thread.currentThread().threadId(), before = bean.getThreadAllocatedBytes(thread);
        for (int i = 0; i < 1000; i++) scalar.step(start, end, 7);
        long allocated = bean.getThreadAllocatedBytes(thread) - before;
        if (allocated > 1024) throw new AssertionError("Solver allocated " + allocated + " bytes");
        before = bean.getThreadAllocatedBytes(thread);
        for (int i = 0; i < 1000; i++) reference(points, previous, start, end, 7);
        long baseline = bean.getThreadAllocatedBytes(thread) - before;
        System.out.println("Rope solver: " + allocated + " bytes / 1000 steps; original: " + baseline + " bytes");
    }

    private static void reference(Vec3[] points, Vec3[] previous, Vec3 start, Vec3 end, double length) {
        points[0] = previous[0] = start; points[24] = previous[24] = end;
        for (int i = 1; i < 24; i++) {
            Vec3 current = points[i];
            points[i] = current.add(current.subtract(previous[i]).scale(.97)).add(0, -.009, 0);
            previous[i] = current;
        }
        for (int pass = 0; pass < 16; pass++) {
            points[0] = previous[0] = start; points[24] = previous[24] = end;
            for (int i = 0; i < 24; i++) {
                Vec3 delta = points[i + 1].subtract(points[i]);
                double distance = delta.length();
                if (distance < 1e-8) continue;
                Vec3 correction = delta.scale((distance - length / 24) / distance);
                if (i == 0) points[i + 1] = points[i + 1].subtract(correction);
                else if (i + 1 == 24) points[i] = points[i].add(correction);
                else {
                    points[i] = points[i].add(correction.scale(.5));
                    points[i + 1] = points[i + 1].subtract(correction.scale(.5));
                }
            }
        }
        points[0] = previous[0] = start; points[24] = previous[24] = end;
    }
}
