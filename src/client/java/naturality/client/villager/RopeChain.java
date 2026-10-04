package naturality.client.villager;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Client-side Verlet rope: pinned ends, gravity, inertia and length constraints. */
public final class RopeChain {
    private static final int SEGMENTS = 24;
    private static final Map<Object, RopeChain> CHAINS = new HashMap<>();
    private static long lastPrune;
    private final Vec3[] points = new Vec3[SEGMENTS + 1], previous = new Vec3[SEGMENTS + 1];
    private long lastTime, lastSeen;
    private double remainder, length;
    private Object level;

    public static Vec3[] sample(Object key, Vec3 start, Vec3 end) {
        long now = System.nanoTime();
        if (now - lastPrune > 1_000_000_000L) {
            CHAINS.entrySet().removeIf(entry -> now - entry.getValue().lastSeen > 5_000_000_000L);
            lastPrune = now;
        }
        RopeChain chain = CHAINS.computeIfAbsent(key, ignored -> new RopeChain());
        chain.update(start, end, now);
        return chain.points.clone();
    }

    private void update(Vec3 start, Vec3 end, long now) {
        var client = Minecraft.getInstance();
        double desired = start.distanceTo(end) * 1.06 + .12;
        if (points[0] == null || level != client.level || points[0].distanceToSqr(start) > 64
                || points[SEGMENTS].distanceToSqr(end) > 64) {
            level = client.level;
            length = desired;
            for (int i = 0; i <= SEGMENTS; i++) {
                double t = i / (double)SEGMENTS;
                points[i] = previous[i] = start.lerp(end, t).add(0, -.15 * Math.sin(t * Math.PI), 0);
            }
            lastTime = now;
            remainder = 0;
        }
        double elapsed = Math.min(.1, (now - lastTime) / 1_000_000_000.0);
        lastTime = lastSeen = now;
        if (!client.isPaused()) remainder += elapsed;
        length = Math.max(desired, length * .98 + desired * .02);
        pin(start, end);
        while (remainder >= .025) {
            remainder -= .025;
            for (int i = 1; i < SEGMENTS; i++) {
                Vec3 current = points[i];
                points[i] = current.add(current.subtract(previous[i]).scale(.97)).add(0, -.009, 0);
                previous[i] = current;
            }
            for (int pass = 0; pass < 16; pass++) {
                pin(start, end);
                for (int i = 0; i < SEGMENTS; i++) {
                    Vec3 delta = points[i + 1].subtract(points[i]);
                    double distance = delta.length();
                    if (distance < 1.0e-8) continue;
                    Vec3 correction = delta.scale((distance - length / SEGMENTS) / distance);
                    if (i == 0) points[i + 1] = points[i + 1].subtract(correction);
                    else if (i + 1 == SEGMENTS) points[i] = points[i].add(correction);
                    else {
                        points[i] = points[i].add(correction.scale(.5));
                        points[i + 1] = points[i + 1].subtract(correction.scale(.5));
                    }
                }
                if (pass % 4 == 3) collide();
            }
            collide();
        }
        pin(start, end);
    }

    private void collide() {
        var world = Minecraft.getInstance().level;
        if (world == null) return;
        for (int i = 1; i < SEGMENTS; i++) {
            Vec3 before = points[i];
            var area = new AABB(previous[i], before).inflate(.02);
            for (var shape : world.getBlockCollisions(null, area))
                for (var box : shape.toAabbs()) points[i] = resolveCollision(points[i], previous[i], box);
            Vec3 correction = points[i].subtract(before);
            if (correction.lengthSqr() > 1.0e-12) {
                // Remove inward velocity while retaining damped sliding motion.
                Vec3 normal = correction.normalize();
                Vec3 velocity = before.subtract(previous[i]);
                velocity = velocity.subtract(normal.scale(Math.min(0, velocity.dot(normal)))).scale(.8);
                previous[i] = points[i].subtract(velocity);
            }
        }
    }

    /** Swept contact plus nearest-face depenetration; never force links onto a block's roof. */
    public static Vec3 resolveCollision(Vec3 point, Vec3 old, AABB solid) {
        AABB box = solid.inflate(.015);
        if (!box.contains(old)) {
            var hit = box.clip(old, point);
            if (hit.isPresent()) point = hit.get();
        }
        if (!box.inflate(.00001).contains(point)) return point;
        double[] distances = {Math.abs(point.x - box.minX), Math.abs(box.maxX - point.x),
            Math.abs(point.y - box.minY), Math.abs(box.maxY - point.y),
            Math.abs(point.z - box.minZ), Math.abs(box.maxZ - point.z)};
        int face = 0;
        for (int i = 1; i < distances.length; i++) if (distances[i] < distances[face]) face = i;
        return switch (face) {
            case 0 -> new Vec3(box.minX - .0001, point.y, point.z);
            case 1 -> new Vec3(box.maxX + .0001, point.y, point.z);
            case 2 -> new Vec3(point.x, box.minY - .0001, point.z);
            case 3 -> new Vec3(point.x, box.maxY + .0001, point.z);
            case 4 -> new Vec3(point.x, point.y, box.minZ - .0001);
            default -> new Vec3(point.x, point.y, box.maxZ + .0001);
        };
    }

    private void pin(Vec3 start, Vec3 end) {
        points[0] = previous[0] = start;
        points[SEGMENTS] = previous[SEGMENTS] = end;
    }
    private RopeChain() { }
}
