package naturality.client.portal;

import java.util.*;

/** Geometry in portal coordinates: horizontal position and height, in block units. */
public final class PortalGlowOcclusion {
    public static final double HALO_OFFSET = 1.0 / 512.0;
    // The rays must begin in front of the halo, not pass through its plane.
    public static final double RAY_ROOT_OFFSET = HALO_OFFSET + 1.0 / 1024.0;
    public record Rect(double left, double bottom, double right, double top) {
        public boolean contains(double u, double y) {
            return u > left && u < right && y > bottom && y < top;
        }
    }
    public record Interval(double start, double end) { }

    /** Subtract the union of blockers, retaining sub-pixel endpoints without gaps or overlaps. */
    public static List<Interval> uncovered(double start, double end, List<Interval> blockers) {
        List<Interval> sorted = new ArrayList<>(blockers);
        sorted.sort(Comparator.comparingDouble(Interval::start));
        List<Interval> result = new ArrayList<>();
        double cursor = start;
        for (Interval block : sorted) {
            if (block.end <= cursor || block.start >= end) continue;
            if (block.start > cursor) result.add(new Interval(cursor, Math.min(end, block.start)));
            cursor = Math.max(cursor, block.end);
            if (cursor >= end) break;
        }
        if (cursor < end) result.add(new Interval(cursor, end));
        return result;
    }

    public static Rect pixels(double left, double bottom, double right, double top) {
        return new Rect(Math.floor(left * 16) / 16, Math.floor(bottom * 16) / 16,
            Math.ceil(right * 16) / 16, Math.ceil(top * 16) / 16);
    }

    public static double distance(Rect rect, double u, double y) {
        return Math.max(Math.max(rect.left - u, u - rect.right), Math.max(rect.bottom - y, y - rect.top));
    }
    private PortalGlowOcclusion() { }
}
