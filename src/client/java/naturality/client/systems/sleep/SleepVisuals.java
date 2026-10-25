package naturality.client.systems.sleep;

import java.util.function.Consumer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import naturality.systems.sleep.SleepAcceleration.SleepStatePayload;
import naturality.config.gameplay.GameplaySettings;
import net.minecraft.world.attribute.EnvironmentAttributes;

/** Sleep animation clocks; visual callbacks and particle spawning keep normal cadence. */
public final class SleepVisuals {
    private static boolean accelerating;
    private static double animationSpeed = 1;
    private static double animationSeconds;
    private static double animationBoostSeconds;
    private static long lastAnimationNanos;
    private static Object animationWorld;
    private SleepVisuals() {}
    public static boolean accelerating() { return accelerating; }
    public static double speed() {
        var client = Minecraft.getInstance();
        return accelerating && client.level != null && client.level.tickRateManager().runsNormally()
            ? animationSpeed : 1;
    }
    /** Smooth world animation clock for effects simulated during rendering. */
    public static double seconds() {
        var client = Minecraft.getInstance();
        long now = System.nanoTime();
        if (animationWorld != client.level) { animationWorld = client.level; animationSeconds = animationBoostSeconds = 0; lastAnimationNanos = now; }
        if (lastAnimationNanos != 0 && !client.isPaused() && client.level != null && client.level.tickRateManager().runsNormally()) {
            double elapsed = Math.min(.25, (now - lastAnimationNanos) / 1e9), speed = speed();
            animationSeconds += elapsed * speed;
            animationBoostSeconds += elapsed * (speed - 1);
        }
        lastAnimationNanos = now;
        return animationSeconds;
    }
    public static double boostSeconds() { seconds(); return animationBoostSeconds; }
    public static void register(Consumer<Minecraft> tick) {
        ClientTickEvents.END_CLIENT_TICK.register(tick::accept);
    }
    public static void initialize() {
        ClientPlayConnectionEvents.INIT.register((_, _) -> reset());
        ClientPlayConnectionEvents.DISCONNECT.register((_, _) -> reset());
        if (!naturality.config.startup.FeatureConfig.clientOnly())
            ClientPlayNetworking.registerGlobalReceiver(SleepStatePayload.TYPE, (payload, _) -> accept(payload));
    }
    public static void accept(SleepStatePayload payload) {
        seconds(); // Commit the previous speed before changing it, including wake.
        accelerating = payload.accelerating();
        animationSpeed = Double.isFinite(payload.speed()) ? Math.clamp(payload.speed(), 1, 1000) : 1;
    }
    private static void reset() {
        accelerating = false;
        animationSpeed = 1;
        SleepSnapshots.reset();
        animationWorld = null; animationSeconds = animationBoostSeconds = 0; lastAnimationNanos = 0;
    }

    /** Wall-time easing, independent of accelerated world ticks; safe for the audio thread. */
    public static final class SleepAudio {
        private static volatile float amount;
        private static long last;
        private static Object world;
        private SleepAudio() {}
        public static float amount() { return amount; }
        public static float gain(float amount) { return (float)Math.pow(.35, Math.clamp(amount, 0, 1)); }
        public static float highFrequencyGain(float amount) { return (float)Math.pow(.01, Math.clamp(amount, 0, 1)); }
        public static float ease(float value, float target, double seconds) {
            return (float)(target + (value - target) * Math.exp(-Math.max(0, seconds) / .65));
        }
        public static void update() {
            var client = Minecraft.getInstance();
            long now = System.nanoTime();
            if (world != client.level) { world = client.level; amount = 0; last = now; }
            double dt = last == 0 ? 0 : Math.min(.25, (now - last) / 1e9);
            last = now;
            if (client.isPaused()) return;
            float target = client.level != null && client.player != null && client.player.isSleeping()
                && GameplaySettings.displayedSettings().sleepAcceleration() ? 1 : 0;
            amount = ease(amount, target, dt);
            if (Math.abs(amount - target) < .0001) amount = target;
        }
    }

    /** Bounded snapshot interpolation that carries velocity across packet arrivals. */
    public static final class SleepCloudClock {
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

    /** Snapshot interpolation along the short angular arc, including dawn wrap. */
    public static final class SleepSky {
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

    /** Immutable targets captured only after a server time packet arrives. */
    public static final class SleepSnapshots {
        private SleepSnapshots() {}
        public record Snapshot(Object world, long revision, long time, float sun, float moon, float stars) {}
        private static Snapshot latest;
        private static long revision;
        public static Snapshot latest(Object world) { return latest != null && latest.world() == world ? latest : null; }
        public static void reset() { latest = null; }
        public static void capture(long time) {
            var client = Minecraft.getInstance();
            var level = client.level;
            if (level == null) return;
            var position = client.gameRenderer.mainCamera().position();
            var attributes = level.environmentAttributes();
            float radians = (float)(Math.PI / 180);
            latest = new Snapshot(level, ++revision, time,
                attributes.getValue(EnvironmentAttributes.SUN_ANGLE, position, null) * radians,
                attributes.getValue(EnvironmentAttributes.MOON_ANGLE, position, null) * radians,
                attributes.getValue(EnvironmentAttributes.STAR_ANGLE, position, null) * radians);
        }
    }
}
