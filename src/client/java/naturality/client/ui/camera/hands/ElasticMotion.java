package naturality.client.ui.camera.hands;

import java.util.ArrayDeque;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.InteractionHand;

/** Superposed elasticOut step responses keep moving targets continuous. */
public final class ElasticMotion {
    public static final double DURATION = 18;
    private record Step(double time, float[] delta) {}
    private final ArrayDeque<Step> steps = new ArrayDeque<>();
    private float[] base, target;
    private double lastTime;
    private double duration = DURATION;
    private double strength = 1;

    public void configure(int speedPercent, int strengthPercent) {
        double nextDuration = DURATION * 100 / Math.clamp(speedPercent, 25, 300);
        double nextStrength = Math.clamp(strengthPercent, 0, 200) / 100.0;
        if (duration != nextDuration || strength != nextStrength) reset();
        duration = nextDuration;
        strength = nextStrength;
    }

    public static double elasticOut(double t) {
        if (t <= 0) return 0;
        if (t >= 1) return 1;
        double oscillation = Math.pow(2, -10 * t) * Math.sin((t * 10 - .75) * (2 * Math.PI / 3));
        // Reach half amplitude by the first target crossing, without a jump at t=0.
        double damping = Math.min(1, t / .075);
        double strength = 1 - .5 * damping * damping * (3 - 2 * damping);
        return 1 + oscillation * strength;
    }

    public float[] sample(float[] next, double time) {
        if (base == null || time < lastTime || time - lastTime > 2) {
            base = next.clone(); target = next.clone(); steps.clear(); lastTime = time;
        }
        while (!steps.isEmpty() && time - steps.peekFirst().time >= duration) {
            var step = steps.removeFirst();
            for (int i = 0; i < base.length; i++) base[i] += step.delta[i];
        }
        if (time > lastTime) {
            var delta = new float[next.length];
            boolean changed = false;
            for (int i = 0; i < delta.length; i++) { delta[i] = next[i] - target[i]; changed |= Math.abs(delta[i]) > .000001F; }
            if (changed) {
                // Bound the response history even at unusually high frame rates.
                if (steps.size() >= 384) {
                    var oldest = steps.removeFirst();
                    for (int i = 0; i < base.length; i++) base[i] += oldest.delta[i];
                }
                steps.addLast(new Step(time, delta)); target = next.clone();
            }
            lastTime = time;
        }
        float[] result = base.clone();
        for (var step : steps) {
            double weightValue = elasticOut((time - step.time) / duration);
            // Scale overshoot without changing the approach or exact endpoints.
            float weight = (float)(weightValue > 1 ? 1 + (weightValue - 1) * strength : weightValue);
            for (int i = 0; i < result.length; i++) result[i] += step.delta[i] * weight;
        }
        return result;
    }
    public void reset() { base = target = null; steps.clear(); }

    public static final class ElasticHandPose {
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
        public void configure(int speedPercent, int strengthPercent) { motion.configure(speedPercent, strengthPercent); }
        public void reset() { motion.reset(); lastRotation.identity(); }
    }

    @org.jspecify.annotations.NullMarked
    public interface ElasticHandsState {
        void naturality$easeMeasuredItem(PoseStack pose, InteractionHand hand);
    }
}
