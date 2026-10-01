package naturality.client.weather;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Local sky access for weather audio, including the open sides of overhangs. */
public final class WeatherSoundEnvironment {
    private static final double[][] DIRECTIONS = {
        {1, 0}, {-1, 0}, {0, 1}, {0, -1},
        {.70710678, .70710678}, {.70710678, -.70710678},
        {-.70710678, .70710678}, {-.70710678, -.70710678}
    };
    private static float indoor = 1;
    private static boolean initialized;

    private WeatherSoundEnvironment() {}

    public static void reset() { indoor = 1; initialized = false; }
    public static float indoor() { return indoor; }
    public static float windExposure() { return .15F + .85F * (1 - indoor); }
    /** Share the same outdoor test between particle shelter and player ambience. */
    public static boolean hasOpenSkyAccess(Level level, Vec3 origin, Entity context) {
        return openSkyExposure(level, origin, context) > 0;
    }

    private static float openSkyExposure(Level level, Vec3 origin, Entity context) {
        if (level.canSeeSky(BlockPos.containing(origin))) return 1;
        for (double distance : new double[] {2, 4, 8}) {
            float reach = distance <= 2 ? 1 : distance <= 4 ? .85F : .65F;
            for (double[] direction : DIRECTIONS) {
                Vec3 sample = origin.add(direction[0] * distance, 0, direction[1] * distance);
                if (!level.canSeeSky(BlockPos.containing(sample))) continue;
                if (level.clip(new ClipContext(origin, sample, ClipContext.Block.COLLIDER,
                        ClipContext.Fluid.NONE, context)).getType() == HitResult.Type.MISS)
                    return reach;
            }
        }
        return 0;
    }

    public static void tick(Minecraft client) {
        var level = client.level;
        var cameraEntity = client.getCameraEntity();
        if (level == null || cameraEntity == null) { reset(); return; }
        Vec3 eye = client.gameRenderer.mainCamera().position();
        float open = openSkyExposure(level, eye, cameraEntity);
        float target = 1 - open;
        if (!initialized) { indoor = target; initialized = true; }
        else indoor += Math.clamp(target - indoor, -.08F, .08F);
    }
}
