package naturality.client.sleep;

/** Bounded snapshot interpolation that carries velocity across packet arrivals. */
public final class SleepCloudClock {
    private static final double DURATION = .12;
    private double value, start, target, elapsed, packetElapsed, velocity, startTangent, endTangent;
    private long revision = Long.MIN_VALUE;
    private boolean easing, hasInterval;
    public double sample(double snapshot, long version, double seconds, boolean accelerating) {
        if (!accelerating) { easing = false; return snapshot; }
        if (!easing) {
            value = start = target = snapshot; revision = version; easing = true;
            elapsed = packetElapsed = velocity = startTangent = endTangent = 0; hasInterval = false;
        }
        double dt = Math.max(0, seconds);
        packetElapsed += dt;
        if (revision != version) {
            double observedVelocity = hasInterval && packetElapsed > 0 ? (snapshot - target) / packetElapsed : 0;
            start = value; target = snapshot; elapsed = packetElapsed = 0; revision = version; hasInterval = true;
            double distance = target - start;
            if (distance == 0) startTangent = endTangent = 0;
            else {
                // Nonnegative normalized slopes whose sum is <= 3 keep the
                // cubic monotonic, so noisy packets cannot cause overshoot.
                double a = Math.clamp(velocity * DURATION / distance, 0, 3);
                double b = Math.clamp(observedVelocity * DURATION / distance, 0, 3 - a);
                startTangent = a * distance; endTangent = b * distance;
            }
        }
        elapsed = Math.min(DURATION, elapsed + dt);
        double t = elapsed / DURATION, t2 = t * t, t3 = t2 * t, distance = target - start;
        value = start + distance * (3 * t2 - 2 * t3)
            + startTangent * (t3 - 2 * t2 + t) + endTangent * (t3 - t2);
        velocity = elapsed >= DURATION ? 0 : (distance * (6 * t - 6 * t2)
            + startTangent * (3 * t2 - 4 * t + 1) + endTangent * (3 * t2 - 2 * t)) / DURATION;
        return value;
    }
    public double sample(double snapshot, double seconds, boolean accelerating) {
        return sample(snapshot, Double.doubleToLongBits(snapshot), seconds, accelerating);
    }
}
