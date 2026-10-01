package naturality.client.sky;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Fixed world-space Catmull-Rom ribbons, tessellated into broad planar curtain panels. */
public final class AuroraGeometry {
    public static final int TILE = 1024;
    public record Point(float x, float y, float z) {}
    public record Panel(Point a, Point b, float topA, float topB, float height, int family, float palette) {}
    private AuroraGeometry() {}
    public static Point spline(Point a, Point b, Point c, Point d, float t) {
        if (t <= 0) return b;
        if (t >= 1) return c;
        return new Point(sample(a.x,b.x,c.x,d.x,t), sample(a.y,b.y,c.y,d.y,t), sample(a.z,b.z,c.z,d.z,t));
    }
    private static float sample(float a, float b, float c, float d, float t) {
        return .5F * (2*b + (-a+c)*t + (2*a-5*b+4*c-d)*t*t + (-a+3*b-3*c+d)*t*t*t);
    }
    public static List<Panel> tile(int tileX, int tileZ) {
        return tile(tileX, tileZ, 2);
    }
    public static List<Panel> tile(int tileX, int tileZ, int segments) {
        segments = Math.clamp(segments, 1, 12);
        var random = new Random(tileX * 341873128712L ^ tileZ * 132897987541L ^ 0x4155524FL);
        var result = new ArrayList<Panel>();
        for (int family = 0; family < 3; family++) {
            float center = 170 + family * 310;
            float base = 370 + family * 32 + random.nextFloat() * 24;
            float height = 112 + random.nextFloat() * 48;
            float palette = random.nextFloat();
            var points = new Point[7];
            for (int i = 0; i < 7; i++) points[i] = new Point(-256 + i * 256,
                base + random.nextFloat() * 28, center + (random.nextFloat() - .5F) * 240);
            ribbon(result, points, height, family, palette, false, segments, tileX, tileZ);
            // A second arm shares the joining point and tangent, ending there instead of doubling the trunk.
            ribbon(result, points, height, family, palette, true, segments, tileX, tileZ);
        }
        return result;
    }
    private static Point branchOffset(Point p, boolean branch) {
        if (!branch) return p;
        float t = Math.clamp(p.x / 512, 0, 1);
        float offset = 1 - t*t*(3-2*t);
        return new Point(p.x, p.y + 18*offset, p.z - 220*offset);
    }
    private static float topHeight(float base, int tileX, int tileZ, int family, float x) {
        long hash = tileX * 341873128712L ^ tileZ * 132897987541L
            ^ family * 0x9E3779B97F4A7C15L ^ Float.floatToIntBits(x);
        hash ^= hash >>> 30; hash *= 0xBF58476D1CE4E5B9L;
        hash ^= hash >>> 27; hash *= 0x94D049BB133111EBL;
        hash ^= hash >>> 31;
        return base * (.91F + .18F * ((hash >>> 40) / (float)0xFFFFFF));
    }
    private static void ribbon(List<Panel> result, Point[] control, float height, int family, float palette,
            boolean branch, int segments, int tileX, int tileZ) {
        for (int section = 1; section < (branch ? 3 : control.length - 2); section++) {
            Point previous = branchOffset(control[section], branch);
            for (int i = 1; i <= segments; i++) {
                Point next = branchOffset(spline(control[section-1], control[section], control[section+1], control[section+2], i / (float)segments), branch);
                result.add(new Panel(previous, next, topHeight(height, tileX, tileZ, family, previous.x()),
                    topHeight(height, tileX, tileZ, family, next.x()), height, family, palette));
                previous = next;
            }
        }
    }
}
