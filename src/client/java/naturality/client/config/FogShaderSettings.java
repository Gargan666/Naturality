package naturality.client.config;

import naturality.config.NaturalityConfig;

public final class FogShaderSettings {
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
