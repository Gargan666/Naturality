package naturality.client.config;

import java.util.Locale;
import naturality.config.NaturalityConfig;

/** Immutable shader text for one resource reload; config values never enter GLSL unchecked. */
public final class LightingShaderSettings {
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
