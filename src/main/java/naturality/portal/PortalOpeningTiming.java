package naturality.portal;

/** Durations in game ticks; shared by the server lock and client animation. */
public final class PortalOpeningTiming {
    public static final int FIRE_TICKS = 10;
    public static final int REVEAL_TICKS = 20;
    public static final int FLASH_TICKS = 40;
    public static final int READY_TICK = FIRE_TICKS + REVEAL_TICKS;
    public static final int END_TICK = READY_TICK + FLASH_TICKS;

    private PortalOpeningTiming() { }
    public static float clamp(float x) { return Math.max(0, Math.min(1, x)); }
    public static float expoIn(float t) {
        t = clamp(t);
        return t == 0 ? 0 : t == 1 ? 1 : (float) Math.pow(2, 10 * (t - 1));
    }
    public static float expoOut(float t) {
        t = clamp(t);
        return t == 1 ? 1 : 1 - (float) Math.pow(2, -10 * t);
    }
    public static float reveal(float age) { return clamp((age - FIRE_TICKS) / REVEAL_TICKS); }
    public static float fireAlpha(float age) { return 1 - clamp(age / FIRE_TICKS); }
    public static boolean ready(float age) { return age >= READY_TICK; }
    public static float pulse(float age) {
        return !ready(age) ? 0 : 1 - expoOut((age - READY_TICK) / FLASH_TICKS);
    }
    public static float pixelAlpha(float progress, float luminance) {
        // Luminance is normalized to the active texture's darkest/lightest texels.
        progress = clamp(progress);
        float brightness = clamp(luminance);
        if (brightness <= 0.5F) {
            float blend = brightness * 2;
            return expoIn(progress) * (1 - blend) + progress * blend;
        }
        float blend = (brightness - 0.5F) * 2;
        return progress * (1 - blend) + expoOut(progress) * blend;
    }
}
