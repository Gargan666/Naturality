package naturality.client.lighting;

import naturality.config.NaturalityConfig;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.level.MoonPhase;
import net.minecraft.world.level.dimension.DimensionType;

/** Shared, smoothly transitioning moonlight multiplier for the entire atmosphere. */
public final class HardcoreDarkness {
    private HardcoreDarkness() {}

    public static float moonBrightness(MoonPhase phase) {
        var moon = NaturalityConfig.get().hardcoreDarkness.moon;
        int percent = switch (phase) {
            case FULL_MOON -> moon.fullPercent;
            case WANING_GIBBOUS, WAXING_GIBBOUS -> moon.gibbousPercent;
            case THIRD_QUARTER, FIRST_QUARTER -> moon.quarterPercent;
            case WANING_CRESCENT, WAXING_CRESCENT -> moon.crescentPercent;
            case NEW_MOON -> moon.newPercent;
        };
        return Math.clamp(percent, 0, 100) / 100.0F;
    }

    public static boolean cavesEnabled(ClientLevel level) {
        var caves = NaturalityConfig.get().hardcoreDarkness.caves;
        return caves.enabled && caves.dimensions.includes(level.dimension());
    }

    public static float ambientMultiplier(ClientLevel level) {
        var caves = NaturalityConfig.get().hardcoreDarkness.caves;
        return cavesEnabled(level) ? Math.clamp(caves.ambientPercent, 0, 100) / 100.0F : 1.0F;
    }

    public static float caveFog(ClientLevel level, Camera camera) {
        var caves = NaturalityConfig.get().hardcoreDarkness.caves;
        var fluid = camera.getFluidInCamera();
        if (!cavesEnabled(level) || !caves.darkenFog
                || fluid == net.minecraft.world.level.material.FogType.LAVA
                || fluid == net.minecraft.world.level.material.FogType.POWDER_SNOW) return 1.0F;
        float sky = level.getBrightness(net.minecraft.world.level.LightLayer.SKY, camera.blockPosition()) / 15.0F;
        float block = level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK, camera.blockPosition()) / 15.0F;
        float light = Math.max(sky, block);
        return light + (1.0F - light) * ambientMultiplier(level);
    }

    public static float sky(ClientLevel level, Camera camera, float partialTicks) {
        return NaturalityConfig.get().hardcoreDarkness.moon.darkenSky ? atmosphere(level, camera, partialTicks) : 1.0F;
    }

    public static float clouds(ClientLevel level, Camera camera, float partialTicks) {
        return NaturalityConfig.get().hardcoreDarkness.moon.darkenClouds ? atmosphere(level, camera, partialTicks) : 1.0F;
    }

    public static float atmosphere(ClientLevel level, Camera camera, float partialTicks) {
        if (!NaturalityConfig.get().hardcoreDarkness.moon.enabled
                || !NaturalityConfig.get().hardcoreDarkness.moon.dimensions.includes(level.dimension())) return 1.0F;
        // Dimensions without a moving sun follow the synchronized Overworld cycle when opted in.
        if (level.dimensionType().skybox() != DimensionType.Skybox.OVERWORLD) {
            long ticks = level.getOverworldClockTime();
            double day = Math.floorMod(ticks, 24000L) / 24000.0 - 0.25;
            day -= Math.floor(day);
            float angle = (float) ((day * 2.0 + 0.5 - Math.cos(day * Math.PI) / 2.0) / 3.0 * 360.0);
            MoonPhase phase = MoonPhase.values()[(int) Math.floorMod(Math.floorDiv(ticks, 24000L), 8L)];
            return nightMultiplier(angle, phase);
        }
        var probe = camera.attributeProbe();
        float angle = probe.getValue(EnvironmentAttributes.SUN_ANGLE, partialTicks);
        return nightMultiplier(angle, probe.getValue(EnvironmentAttributes.MOON_PHASE, partialTicks));
    }

    private static float nightMultiplier(float angle, MoonPhase phase) {
        float night = Mth.clamp(0.5F - 2.0F * Mth.cos(angle * Mth.DEG_TO_RAD), 0.0F, 1.0F);
        night = night * night * (3.0F - 2.0F * night);
        return Mth.lerp(night, 1.0F, moonBrightness(phase));
    }
}
