package naturality.client.config;
import naturality.config.NaturalityConfig;
/** Settings shared by optional effects; applied together on resource reload. */
public final class FeatureShaderSettings {
    private FeatureShaderSettings() {}
    public static String source() {
        var c = NaturalityConfig.get();
        return "#ifndef NATURALITY_FEATURE_SETTINGS\n#define NATURALITY_FEATURE_SETTINGS\n"
            + "const bool NATURALITY_EMISSIVE_LAVA = " + (c.liquids.lava && c.liquids.emissiveLava) + ";\n"
            + "const bool NATURALITY_PIXEL_GLINT_ENABLED = " + c.effects.pixelGlint + ";\n"
            + "const bool NATURALITY_STAR_PULSES = " + c.effects.starPulses + ";\n"
            + "const bool NATURALITY_END_PARALLAX = " + c.portalChanges.endParallax + ";\n"
            + "const bool NATURALITY_FIRE_VARIATION = " + c.effects.fireAnimation + ";\n#endif\n";
    }
}
