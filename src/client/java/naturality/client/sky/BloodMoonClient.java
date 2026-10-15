package naturality.client.sky;

import naturality.sky.SkyEventType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.level.Level;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/** Shared eased intensity for moon scale/color, world skylight and cloud rims. */
public final class BloodMoonClient {
    private static @org.jspecify.annotations.Nullable ClientLevel world;
    private static float previous, amount;
    private BloodMoonClient() {}
    public static void initialize() {
        naturality.client.sleep.SleepVisuals.register(BloodMoonClient::tick);
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register((_, _) -> reset());
    }
    private static void reset() { world = null; previous = amount = 0; }
    private static void tick(Minecraft client) {
        var level = client.level;
        if (world != level) { reset(); world = level; }
        if (level == null || client.isPaused()) return;
        previous = amount;
        float target = 0;
        if (level.dimension().equals(Level.OVERWORLD)) {
            float sun = client.gameRenderer.mainCamera().attributeProbe().getValue(EnvironmentAttributes.SUN_ANGLE, 1);
            float night = Math.clamp(-(float)Math.cos(Math.toRadians(sun)) * 3, 0, 1);
            night = night * night * (3 - 2 * night);
            target = SkyEventsClient.strength(level, SkyEventType.BLOOD_MOON) > 0 ? night : 0;
        }
        amount += (target - amount) * .05F;
        if (Math.abs(target - amount) < .001F) amount = target;
    }
    public static float sample(float partial) {
        return world == Minecraft.getInstance().level && world != null && world.dimension().equals(Level.OVERWORLD)
            ? previous + (amount - previous) * partial : 0;
    }
    public static float current() { return sample(1); }
    public static float scale(float partial) { return 1 + .1F * sample(partial); }
    public static Vector3f tint(Vector3fc original, float partial) {
        float brightness = Math.max(original.x(), Math.max(original.y(), original.z()));
        return new Vector3f(original).lerp(new Vector3f(brightness, 0, 0), sample(partial));
    }
    public static int tintArgb(int color, float partial) {
        var red = tint(new Vector3f((color >>> 16) & 255, (color >>> 8) & 255, color & 255), partial);
        return (color & 0xff000000) | (Math.round(red.x) << 16) | (Math.round(red.y) << 8) | Math.round(red.z);
    }
    public static Vector3f lightingTint(Vector3fc original, float partial) {
        return new Vector3f(original).lerp(tint(original, partial), .5F);
    }
}
