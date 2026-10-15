package naturality.client.sleep;
/** Snapshot interpolation along the short angular arc, including dawn wrap. */
public final class SleepSky {
    private SleepSky() {}
    public static final class Angle {
        private final SleepCloudClock interpolation = new SleepCloudClock();
        private double unwrapped;
        private float previous;
        private long revision = Long.MIN_VALUE;
        private boolean initialized;
        public boolean settled() { return !initialized; }
        public float sample(float snapshot, long version, double seconds, boolean smoothing) {
            if (!smoothing) { initialized = false; interpolation.sample(snapshot, version, 0, false); return snapshot; }
            if (!initialized) { unwrapped = previous = snapshot; revision = version; initialized = true; }
            if (revision != version) {
                unwrapped += Math.atan2(Math.sin((double)snapshot - previous), Math.cos((double)snapshot - previous));
                previous = snapshot; revision = version;
            }
            return (float)interpolation.sample(unwrapped, version, seconds, true);
        }
        public float sample(float snapshot, double seconds, boolean smoothing) {
            return sample(snapshot, Float.floatToIntBits(snapshot), seconds, smoothing);
        }
    }
}
