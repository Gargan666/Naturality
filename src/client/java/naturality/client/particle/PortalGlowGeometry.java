package naturality.client.particle;

import net.minecraft.core.Direction;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Fixed frame geometry; local Y follows portal depth, local Z points inward. */
public final class PortalGlowGeometry {
    // Bounded surface-normal separation: avoids coplanar depth fighting without
    // a slope-dependent camera-depth bias that can penetrate other blocks.
    public static final double INSET = 1.0 / 1024.0;
    public static final int SLICES_PER_BLOCK = 16;
    // +/- 1.5 pixels gives a three-pixel peak-to-peak wave.
    public static final float WAVE_AMPLITUDE = 1.5F / 16.0F;
    public static final float BASE_REACH = 0.875F;
    public static final float PORTAL_SURFACE = 0.125F;
    public static final double FACE_EPSILON = 0.0001;
    private static final Direction[] X_EDGES = {
        Direction.DOWN, Direction.UP, Direction.WEST, Direction.EAST
    };
    private static final Direction[] Z_EDGES = {
        Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH
    };

    private PortalGlowGeometry() { }

    /** Clip the rear glow at the portal slab, independently of translucent draw order. */
    public static int visibleDepthSide(double cameraDepth) {
        if (cameraDepth > PORTAL_SURFACE) return 1;
        if (cameraDepth < -PORTAL_SURFACE) return -1;
        return 0;
    }

    public static float expoOut(float progress) {
        if (progress <= 0) return 0;
        if (progress >= 1) return 1;
        return (float) (1 - Math.pow(2, -10 * progress));
    }

    public static Direction[] edges(Direction.Axis axis) {
        return (axis == Direction.Axis.X ? X_EDGES : Z_EDGES).clone();
    }

    public static Vector3f depth(Direction.Axis axis) {
        return axis == Direction.Axis.X ? new Vector3f(0, 0, 1) : new Vector3f(1, 0, 0);
    }

    public static Quaternionf rotation(Direction.Axis axis, Direction inward) {
        Vector3f normal = new Vector3f(inward.getStepX(), inward.getStepY(), inward.getStepZ());
        Vector3f depth = depth(axis);
        Vector3f tangent = new Vector3f(depth).cross(normal);
        return new Quaternionf().setFromNormalized(new Matrix3f(tangent, depth, normal));
    }

    public static boolean isFront(double cameraX, double cameraY, double cameraZ,
                                  double faceX, double faceY, double faceZ, Direction inward) {
        return (cameraX - faceX) * inward.getStepX()
            + (cameraY - faceY) * inward.getStepY()
            + (cameraZ - faceZ) * inward.getStepZ() > FACE_EPSILON;
    }
}
