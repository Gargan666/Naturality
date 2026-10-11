package naturality.client.cloud;

import java.util.ArrayList;
import naturality.config.NaturalityConfig.CloudLayer;
import net.minecraft.client.renderer.CloudRenderer;
import net.minecraft.world.phys.Vec3;

/** Current-frame cloud occupancy, shared with the camera flare without GPU readback. */
public final class CloudSunOcclusion {
    private record Layer(CloudRenderer.TextureData texture, CloudLayer settings,
            double bottom, double drift, double range) {}
    private static final ArrayList<Layer> LAYERS = new ArrayList<>(2);
    private CloudSunOcclusion() {}
    public static void beginFrame() { LAYERS.clear(); }
    public static void add(CloudRenderer.TextureData texture, CloudLayer settings,
            float bottom, int range, long time, float partial) {
        if (settings.enabled && settings.opacityPercent > 0)
            LAYERS.add(new Layer(texture, settings, bottom + settings.heightOffset,
                (time + (double) partial) * 0.03 * settings.speedPercent / 100.0, range * 16.0));
    }

    public static float transmission(Vec3 eye, Vec3 end) {
        float result = 1;
        for (var layer : LAYERS) result *= 1 - coverage(layer, eye, end.subtract(eye).normalize());
        return result;
    }

    private static float coverage(Layer layer, Vec3 eye, Vec3 ray) {
        var c = layer.settings;
        double top = layer.bottom + (c.style == 2 ? 0 : c.thickness);
        double start, finish;
        if (Math.abs(ray.y) < 1.0E-8) {
            if (eye.y < layer.bottom || eye.y > top || c.style == 2) return 0;
            start = 0; finish = layer.range * 2;
        } else {
            double a = (layer.bottom - eye.y) / ray.y, b = (top - eye.y) / ray.y;
            start = Math.max(0, Math.min(a, b)); finish = Math.max(a, b);
            if (finish < start) return 0;
        }
        double offsetX = c.offsetX + layer.drift, offsetZ = c.offsetZ + 3.96;
        // Traverse each crossed cloud cell, including side intersections at low sun angles.
        double t = start;
        float covered = 0;
        for (int step = 0; step < 1024 && t <= finish; step++) {
            double x = eye.x + ray.x * t + offsetX, z = eye.z + ray.z * t + offsetZ;
            int cx = (int) Math.floor(x / c.width), cz = (int) Math.floor(z / c.width);
            double horizontal = Math.max(Math.abs(ray.x * t), Math.abs(ray.z * t));
            if (horizontal > layer.range + c.width) break;
            var tex = layer.texture;
            if (tex.cells()[Math.floorMod(cx, tex.width()) + Math.floorMod(cz, tex.height()) * tex.width()] != 0) {
                float fog = (float) Math.max(0, 1 - t / layer.range);
                covered = Math.max(covered, c.opacityPercent / 100F * fog);
            }
            if (c.style == 2 || t == finish) break;
            double dx = Math.abs(ray.x) < 1.0E-8 ? Double.POSITIVE_INFINITY
                : ((ray.x > 0 ? cx + 1 : cx) * c.width - x) / ray.x;
            double dz = Math.abs(ray.z) < 1.0E-8 ? Double.POSITIVE_INFINITY
                : ((ray.z > 0 ? cz + 1 : cz) * c.width - z) / ray.z;
            double next = t + Math.min(dx, dz) + 1.0E-5;
            if (next > finish) break;
            t = next;
        }
        return covered;
    }
}
