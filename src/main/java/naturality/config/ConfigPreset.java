package naturality.config;

/** One-shot edits, never a persisted mode that overrides later individual choices. */
public enum ConfigPreset {
    DEFAULT, CLIENTSIDE_ONLY, NO_VISUAL_OVERHAUL;

    public void apply(NaturalityConfig client, NaturalityServerConfig server, FeatureConfig features) {
        client.reset();
        server.reset();
        for (var feature : Feature.values()) features.set(feature,
            this == DEFAULT || (this == CLIENTSIDE_ONLY ? feature.group == Feature.Group.VISUAL
                : feature.group != Feature.Group.VISUAL));
        if (this == CLIENTSIDE_ONLY) {
            server.sleepAcceleration = false;
            server.portalIntroAnimation = server.fireWrapping = server.snowWrapping = false;
            server.snowCompaction = server.weatherThaw = server.weatherSnowAccumulation = false;
            server.vanillaPortalEntry = true;
        } else if (this == NO_VISUAL_OVERHAUL) {
            client.disableVisuals();
            // These combine visual geometry with server collision/travel changes.
            server.portalIntroAnimation = server.fireWrapping = server.snowWrapping = false;
            server.vanillaPortalEntry = true;
            // These server hooks exist to support the optional animation/impact overhaul.
            features.set(Feature.GHAST_PHYSICS, false);
            features.set(Feature.ARROW_IMPACT_EVENTS, false);
            features.set(Feature.FIREBALL_IMPACT_EVENTS, false);
        }
    }
}
