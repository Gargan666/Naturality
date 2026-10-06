package naturality.client.fog;

import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/** Frame-local, conservative full-opacity tests. Never changes world visibility or simulation. */
public final class FogCulling {
    private static volatile Frame frame = new Frame(null, 0, 0, 0, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY);
    private FogCulling() {}

    public static void capture(ClientLevel level, Camera camera, FogData fog, boolean enabled) {
        boolean composite = level.dimension().equals(Level.END) && EndFogTransparency.isActive(level, camera);
        var pos = camera.position();
        boolean opaque = enabled && fog.color.w > 0 && !composite;
        frame = new Frame(level, pos.x, pos.y, pos.z,
            opaque ? endpoint(fog.environmentalStart, fog.environmentalEnd) : Double.POSITIVE_INFINITY,
            opaque ? endpoint(fog.renderDistanceStart, fog.renderDistanceEnd) : Double.POSITIVE_INFINITY);
    }

    private static double endpoint(float start, float end) {
        return Float.isFinite(end) && end > 0 && end >= start ? end : Double.POSITIVE_INFINITY;
    }

    public static boolean hidden(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        Frame current = frame;
        return hidden(current.x, current.y, current.z, current.sphere, current.cylinder,
            minX, minY, minZ, maxX, maxY, maxZ);
    }

    public static boolean matches(@Nullable Level level) { return level != null && frame.level == level; }

    public static boolean active() {
        Frame current = frame;
        return Double.isFinite(current.sphere) || Double.isFinite(current.cylinder);
    }

    public static boolean sectionHidden(int x, int y, int z) {
        if (!sectionHidden(x,y,z,0)) return false;
        int drop = naturality.client.snow.SnowSectionVisibility.below(
            net.minecraft.core.SectionPos.asLong(x >> 4,y >> 4,z >> 4));
        return drop == 0 || sectionHidden(x,y,z,drop);
    }

    public static boolean sectionHidden(int x, int y, int z, int snowDrop) {
        // Fitted snow can reach 32 blocks below its saved section; the ordinary
        // wind/model margin alone does not contain those displaced surfaces.
        return hidden(x - 2.0, y - snowDrop - 2.0, z - 2.0, x + 18.0, y + 18.0, z + 18.0);
    }

    public static boolean hidden(double x, double y, double z, double sphere, double cylinder,
            double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        if (!(minX <= maxX && minY <= maxY && minZ <= maxZ)) return false;
        double dx = Math.max(Math.max(minX - x, x - maxX), 0);
        double dy = Math.max(Math.max(minY - y, y - maxY), 0);
        double dz = Math.max(Math.max(minZ - z, z - maxZ), 0);
        double horizontal = dx * dx + dz * dz;
        // A small guard avoids rounding a boundary fragment into the culled region.
        double sphericalEnd = sphere + 0.25;
        double cylindricalEnd = cylinder + 0.25;
        return horizontal + dy * dy > sphericalEnd * sphericalEnd
            || Math.max(horizontal, dy * dy) > cylindricalEnd * cylindricalEnd;
    }

    private record Frame(@Nullable Level level, double x, double y, double z, double sphere, double cylinder) {}
}
