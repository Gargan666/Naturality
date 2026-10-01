package naturality.client.particle;

import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Matrix3f;
import org.joml.Vector3f;

public final class PortalMoteGeometry {
    private PortalMoteGeometry() { }

    public static double spawnOffset(double normalOffset) {
        return Math.copySign(Math.max(Math.abs(normalOffset),
            PortalGlowGeometry.PORTAL_SURFACE + 0.3001), normalOffset);
    }

    public static float arrivalAlpha(double distance) {
        return (float) Math.clamp(distance / 0.1, 0, 1);
    }

    public static float crossWidth(float proximity) {
        return 0.25F + 0.75F * Math.clamp(proximity, 0, 1);
    }

    public static float paletteU(float proximity) {
        int index = Math.round((1 - Math.clamp(proximity, 0, 1)) * (PortalGlowPalette.SIZE - 1));
        return (index + 0.5F) / PortalGlowPalette.SIZE;
    }

    public static float growth(double distance, double initialDistance) {
        double progress = Math.clamp(1 - distance / Math.max(initialDistance, 0.0001), 0, 1);
        return (float) (progress * progress * (3 - 2 * progress));
    }

    public static Quaternionf orientation(Vec3 velocity, Vec3 toCamera) {
        Vec3 direction = velocity.lengthSqr() < 1.0E-12 ? new Vec3(1, 0, 0) : velocity.normalize();
        Vector3f u = new Vector3f((float) direction.x, (float) direction.y, (float) direction.z);
        Vector3f normal = new Vector3f((float) toCamera.x, (float) toCamera.y, (float) toCamera.z);
        normal.sub(new Vector3f(u).mul(normal.dot(u)));
        if (normal.lengthSquared() < 1.0E-8F) {
            // Exactly head-on: retain a stable plane perpendicular to the viewing ray.
            normal.set(Math.abs(u.y) < 0.9F ? new Vector3f(0, 1, 0) : new Vector3f(0, 0, 1));
            normal.sub(new Vector3f(u).mul(normal.dot(u)));
        }
        normal.normalize();
        Vector3f v = new Vector3f(normal).cross(u).normalize();
        return new Quaternionf().setFromNormalized(new Matrix3f(u, v, normal));
    }
}
