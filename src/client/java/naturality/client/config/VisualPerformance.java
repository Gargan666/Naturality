package naturality.client.config;

import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/** Provisional, qualitative FPS-cost estimates; deliberately not hardware benchmark numbers. */
public final class VisualPerformance {
    public enum Impact {
        LOW(ChatFormatting.GREEN), MEDIUM(ChatFormatting.YELLOW),
        HIGH(ChatFormatting.GOLD), EXTRA_HIGH(ChatFormatting.RED);
        final ChatFormatting color;
        Impact(ChatFormatting color) { this.color = color; }
    }
    private static final Map<String, Impact> ESTIMATES = Map.ofEntries(
        Map.entry("lighting.enabled", Impact.LOW),
        Map.entry("effects.cameraFlare", Impact.LOW),
        Map.entry("feature.ghast_squash", Impact.LOW),
        Map.entry("feature.fireball_models", Impact.LOW),
        Map.entry("feature.mob_death_pixels", Impact.LOW),
        Map.entry("feature.physics_ropes", Impact.LOW),
        Map.entry("clouds.enabled", Impact.MEDIUM),
        Map.entry("feature.pixel_shadows", Impact.MEDIUM),
        Map.entry("liquids.waterDistortion", Impact.MEDIUM),
        Map.entry("effects.snowOverlays", Impact.MEDIUM),
        Map.entry("effects.worldGlintContours", Impact.MEDIUM),
        Map.entry("effects.weatherParticles", Impact.HIGH),
        Map.entry("liquids.heatWaves", Impact.HIGH),
        Map.entry("fog.enabled", Impact.HIGH),
        Map.entry("dynamicLighting.enabled", Impact.EXTRA_HIGH)
    );
    private VisualPerformance() {}

    public static @Nullable Impact estimate(String key) { return ESTIMATES.get(key); }

    public static Component tooltip(String label, Component description) {
        String key = label.replaceFirst("^naturality\\.config\\.", "");
        var impact = estimate(key);
        if (impact == null) return description;
        String tier = impact.name().toLowerCase(java.util.Locale.ROOT);
        return description.copy().append("\n\n")
            .append(Component.translatable("naturality.config.performance.estimate",
                Component.translatable("naturality.config.performance." + tier)).withStyle(impact.color))
            .append("\n").append(Component.translatable("naturality.config.performance." + key))
            .append("\n").append(Component.translatable("naturality.config.performance.qualifier").withStyle(ChatFormatting.GRAY));
    }

    public static Component tooltip(String label) {
        return tooltip(label, Component.translatable(label + ".tooltip"));
    }
}
