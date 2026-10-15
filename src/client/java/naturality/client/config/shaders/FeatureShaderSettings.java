package naturality.client.config.shaders;

import naturality.config.live.NaturalityConfig;
import java.util.Locale;

/** Settings shared by optional effects; applied together on resource reload. */
@org.jspecify.annotations.NullMarked
public final class FeatureShaderSettings {
    private FeatureShaderSettings() {}
    public static String source() {
        var c = NaturalityConfig.get();
        return "#ifndef NATURALITY_FEATURE_SETTINGS\n#define NATURALITY_FEATURE_SETTINGS\n"
            + "const bool NATURALITY_DISTANT_WIND = " + c.effects.distantWind + ";\n"
            + "const bool NATURALITY_EMISSIVE_LAVA = " + (c.liquids.lava && c.liquids.emissiveLava) + ";\n"
            + "const bool NATURALITY_PIXEL_GLINT_ENABLED = " + c.effects.pixelGlint + ";\n"
            + "const bool NATURALITY_STAR_PULSES = " + c.effects.starPulses + ";\n"
            + "const bool NATURALITY_END_PARALLAX = " + c.portalChanges.endParallax + ";\n"
            + "const bool NATURALITY_FIRE_VARIATION = " + c.effects.fireAnimation + ";\n#endif\n";
    }

    /** Immutable shader text for one resource reload; config values never enter GLSL unchecked. */
    public static final class LightingShaderSettings {
        private LightingShaderSettings() {}

        public static String source() {
            var config = NaturalityConfig.get().lighting;
            config.sanitize();
            double dark = config.darkStepPercent / 100;
            double light = config.lightStepPercent / 100;
            double range = light / dark;
            double bands = Math.abs(light - dark) < 1e-8 ? 1 / dark
                : Math.log(range) / (light - dark);
            return "const bool NATURALITY_LIGHTING_ENABLED = " + config.enabled + ";\n" + String.format(Locale.ROOT, """
                const float NATURALITY_AO_STEPS = %.8f;
                const float NATURALITY_LIGHT_BANDS = %.8f;
                const float NATURALITY_LIGHT_RANGE = %.8f;
                const float NATURALITY_DARK_SATURATION = %.8f;
                const float NATURALITY_SATURATION_START = %.8f;
                const float NATURALITY_SATURATION_FULL = %.8f;
                """, 100 / config.aoStepPercent, (double) Math.max(1, Math.round(bands)), range,
                config.darkSaturationPercent / 100, config.saturationStartPercent / 100,
                config.saturationFullPercent / 100);
        }
    }

    public static final class FogShaderSettings {
        private FogShaderSettings() {}

        public static String source() {
            var config = NaturalityConfig.get().fog;
            config.sanitize();
            return "const float NATURALITY_FOG_BORDER_START = " + config.borderStartPercent / 100.0 + ";\n"
                + "const float NATURALITY_FOG_BORDER_FULL = " + config.borderFullPercent / 100.0 + ";\n"
                + "const float NATURALITY_FOG_STEPS = " + config.densitySteps + ".0;\n"
                + "const bool NATURALITY_FOG_ENABLED = " + config.enabled + ";\n"
                + "const float NATURALITY_FOG_PIXEL_SIZE = " + config.pixelSize + ".0;\n";
        }
    }
}
