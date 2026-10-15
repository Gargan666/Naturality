package naturality.config;

/** Filters only resources supplied by Naturality; other mods and user packs are untouched. */
public final class FeatureResources {
    private FeatureResources() {}
    public static boolean allowed(boolean data, String namespace, String path) {
        if (data) {
            if (!Feature.CONTENT.enabled()) {
                // Pure vanilla-entry tags remain useful to independent gameplay/visual features.
                return namespace.equals("naturality") && path.startsWith("tags/");
            }
            return !namespace.equals("minecraft") || !path.equals("worldgen/noise_settings/end.json")
                || Feature.END_TERRAIN.enabled();
        }
        if (!namespace.equals("minecraft")) return true;
        // These descriptions provide sprites required by their independently enabled providers.
        if (path.equals("particles/poof.json")) return Feature.DEATH_SMOKE.enabled();
        if (path.equals("particles/dragon_breath.json")) return Feature.VISUAL_OVERHAUL.enabled();
        if (path.equals("particles/explosion.json") || path.equals("particles/explosion_emitter.json"))
            return Feature.EXPLOSION_PARTICLES.enabled();
        if (path.contains("chorus_flower") || path.contains("chorus_plant")) return Feature.CHORUS_FLOWERS.enabled();
        return Feature.VISUAL_OVERHAUL.enabled()
            && (path.startsWith("shaders/") || Feature.RESOURCE_OVERRIDES.enabled());
    }
}
