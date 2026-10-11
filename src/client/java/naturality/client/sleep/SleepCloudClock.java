package naturality.client.sleep;

/** Frame-time easing of cloud drift; shared by drawing and sun-ray occlusion. */
public final class SleepCloudClock {
    private double value, newestTime;
    private boolean easing;
    public double sample(double target, double seconds, boolean accelerating) {
        if (!accelerating) { easing = false; return target; }
        if (!easing) { value = newestTime = target; easing = true; }
        double dt = Math.max(0, seconds);
        // Server packets can replace a slightly extrapolated native clock with
        // an older sample. Such corrections must never reverse cloud scrolling.
        newestTime = Math.max(newestTime, target);
        value += (newestTime - value) * -Math.expm1(-dt / .12);
        return value;
    }
}
