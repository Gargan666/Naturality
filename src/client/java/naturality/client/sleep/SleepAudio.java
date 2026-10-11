package naturality.client.sleep;

import net.minecraft.client.Minecraft;
import naturality.config.GameplaySettings;

/** Wall-time easing, independent of accelerated world ticks; safe for the audio thread. */
public final class SleepAudio {
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
