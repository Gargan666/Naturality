package naturality.client.snow;

import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;

/** Vertical extent of snow meshes that reach below their owning section. */
public final class SnowSectionVisibility {
    private static final ConcurrentHashMap<Long, Integer> BELOW = new ConcurrentHashMap<>();
    private static final ThreadLocal<Capture> CURRENT = new ThreadLocal<>();
    public static final int MAX_DROP = naturality.snow.SnowGeometry.MAX_DEPTH + 2;

    private record Capture(long section, int originY, int drop) { }
    private SnowSectionVisibility() { }

    public static void begin(SectionPos section) {
        CURRENT.set(new Capture(section.asLong(), section.minBlockY(), 0));
    }

    public static void record(BlockPos owner, double localMinY) {
        var capture = CURRENT.get();
        if (capture == null) return;
        int drop = Math.clamp((int)Math.ceil(capture.originY - owner.getY() - localMinY), 0, MAX_DROP);
        if (drop > capture.drop) CURRENT.set(new Capture(capture.section, capture.originY, drop));
    }

    public static void finish() {
        var capture = CURRENT.get();
        CURRENT.remove();
        if (capture == null) return;
        if (capture.drop == 0) BELOW.remove(capture.section);
        else BELOW.put(capture.section, capture.drop);
    }

    public static int below(long sectionNode) { return BELOW.getOrDefault(sectionNode, 0); }
    public static void clear() { BELOW.clear(); CURRENT.remove(); }
}
