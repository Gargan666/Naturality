package naturality.client.sleep;
import net.minecraft.client.Minecraft;
import net.minecraft.world.attribute.EnvironmentAttributes;
/** Immutable targets captured only after a server time packet arrives. */
public final class SleepSnapshots {
    private SleepSnapshots() {}
    public record Snapshot(Object world, long revision, long time, float sun, float moon, float stars) {}
    private static Snapshot latest;
    private static long revision;
    public static Snapshot latest(Object world) { return latest != null && latest.world() == world ? latest : null; }
    public static void reset() { latest = null; }
    public static void capture(long time) {
        var client = Minecraft.getInstance();
        var level = client.level;
        if (level == null) return;
        var position = client.gameRenderer.mainCamera().position();
        var attributes = level.environmentAttributes();
        float radians = (float)(Math.PI / 180);
        latest = new Snapshot(level, ++revision, time,
            attributes.getValue(EnvironmentAttributes.SUN_ANGLE, position, null) * radians,
            attributes.getValue(EnvironmentAttributes.MOON_ANGLE, position, null) * radians,
            attributes.getValue(EnvironmentAttributes.STAR_ANGLE, position, null) * radians);
    }
}

