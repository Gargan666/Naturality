package naturality.client.sleep;

/** Frame-time angular damping with wrap-safe sun/moon/star rotation. */
public final class SleepSky {
    private SleepSky() {}
    public static final class Angle {
        private double value, observedAngle, newestAngle;
        private float previousTarget;
        private boolean initialized;
        public boolean settled() { return !initialized; }
        public float sample(float target, double seconds, boolean smoothing) {
            if (!smoothing) { initialized = false; return target; }
            if (!initialized) { value = observedAngle = newestAngle = target; previousTarget = target; initialized = true; }
            double delta = Math.atan2(Math.sin((double)target - previousTarget), Math.cos((double)target - previousTarget));
            previousTarget = target;
            observedAngle += delta;
            // Unwrap the authoritative rotation before rejecting corrections;
            // dawn is forward motion, while a rewound packet is not.
            newestAngle = Math.max(newestAngle, observedAngle);
            value += (newestAngle - value) * -Math.expm1(-Math.max(0, seconds) / .12);
            return (float)value;
        }
    }
}
