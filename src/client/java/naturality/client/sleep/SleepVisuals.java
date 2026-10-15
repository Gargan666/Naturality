package naturality.client.sleep;

import java.util.function.Consumer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import naturality.sleep.SleepStatePayload;

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
}
