package naturality.client.camera;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class ElasticHandPose {
    private final ElasticMotion motion = new ElasticMotion();
    private final Quaternionf lastRotation = new Quaternionf();
    public Matrix4f sample(Matrix4f pose, double time) {
        var position = pose.getTranslation(new Vector3f());
        var scale = pose.getScale(new Vector3f());
        var rotation = pose.getUnnormalizedRotation(new Quaternionf()).normalize();
        if (rotation.dot(lastRotation) < 0) rotation.mul(-1);
        lastRotation.set(rotation);
        var values = motion.sample(new float[]{position.x, position.y, position.z,
            rotation.x, rotation.y, rotation.z, rotation.w, scale.x, scale.y, scale.z}, time);
        var easedRotation = new Quaternionf(values[3], values[4], values[5], values[6]);
        if (easedRotation.lengthSquared() < .0001F) easedRotation.set(rotation);
        else easedRotation.normalize();
        var eased = new Matrix4f().translationRotateScale(values[0], values[1], values[2], easedRotation.x,
            easedRotation.y, easedRotation.z, easedRotation.w,
            Math.max(.0001F, values[7]), Math.max(.0001F, values[8]), Math.max(.0001F, values[9]));
        // Preserve native residual shear (e.g. stretched bow/crossbow transforms).
        var targetPose = new Matrix4f().translationRotateScale(position, rotation, scale);
        return eased.mul(targetPose.invert().mul(pose));
    }
    public void reset() { motion.reset(); lastRotation.identity(); }
}
