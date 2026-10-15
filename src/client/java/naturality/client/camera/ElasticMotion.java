package naturality.client.camera;

import java.util.ArrayDeque;

/** Superposed elasticOut step responses keep moving targets continuous. */
public final class ElasticMotion {
    public static final double DURATION = 18;
    private record Step(double time, float[] delta) {}
    private final ArrayDeque<Step> steps = new ArrayDeque<>();
    private float[] base, target;
    private double lastTime;

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
        while (!steps.isEmpty() && time - steps.peekFirst().time >= DURATION) {
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
            float weight = (float)elasticOut((time - step.time) / DURATION);
            for (int i = 0; i < result.length; i++) result[i] += step.delta[i] * weight;
        }
        return result;
    }
    public void reset() { base = target = null; steps.clear(); }
}
