package naturality.config.startup;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.LoggerFactory;

/** No Minecraft classes: also safe to read before block/entity classes are transformed. */
@org.jspecify.annotations.NullMarked
public final class FeatureConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("naturality-features.json");
    private static final FeatureConfig INSTANCE = load();
    private static final Map<Feature, Boolean> ACTIVE = snapshot(INSTANCE);
    public Map<String, Boolean> features = new LinkedHashMap<>();

    public FeatureConfig() { for (var feature : Feature.values()) features.put(feature.key(), true); }
    public boolean get(Feature feature) { return feature.available() && !Boolean.FALSE.equals(features.get(feature.key())); }
    public void set(Feature feature, boolean value) { features.put(feature.key(), value); }
    public boolean effective(Feature feature) {
        return get(feature) && (feature.parent == null || effective(feature.parent));
    }
    public static FeatureConfig get() { return INSTANCE; }
    public static boolean active(Feature feature) { return Boolean.TRUE.equals(ACTIVE.get(feature)); }
    public boolean restartRequired() { return !ACTIVE.equals(snapshot(this)); }
    private static Map<Feature, Boolean> snapshot(FeatureConfig config) {
        var result = new EnumMap<Feature, Boolean>(Feature.class);
        for (var feature : Feature.values()) result.put(feature, config.effective(feature));
        return result;
    }
    private static FeatureConfig load() {
        if (Files.exists(FILE)) try (var reader = Files.newBufferedReader(FILE)) {
            var config = GSON.fromJson(reader, FeatureConfig.class);
            if (config != null && config.features != null) {
                // Preserve the old combined scheduler/visual choices when separating gameplay events.
                if (!config.features.containsKey(Feature.EVENT_CYCLES.key())) {
                    config.features.put(Feature.EVENT_CYCLES.key(), config.get(Feature.SKY_EVENT_CYCLES));
                    config.features.put(Feature.BLOOD_MOON.key(),
                        config.get(Feature.BLOOD_MOON) && config.get(Feature.SKY_EVENTS));
                }
                for (var feature : Feature.values()) config.features.putIfAbsent(feature.key(), true);
                return config;
            }
        } catch (Exception error) {
            LoggerFactory.getLogger("naturality").error("Could not read feature config; using defaults", error);
        }
        return new FeatureConfig();
    }
    public void save() {
        try {
            Files.createDirectories(FILE.getParent());
            Path temp = Files.createTempFile(FILE.getParent(), "naturality-features-", ".tmp");
            try {
                Files.writeString(temp, GSON.toJson(this) + System.lineSeparator());
                Files.move(temp, FILE, StandardCopyOption.REPLACE_EXISTING);
            } finally { Files.deleteIfExists(temp); }
        } catch (Exception error) {
            LoggerFactory.getLogger("naturality").error("Could not save feature config", error);
        }
    }

    /** Startup features. Presets only edit these ordinary options and the existing settings. */
    public enum Feature {
        CONTENT(Group.CONTENT),
        MOUNTAIN_WATERFALLS(Group.CONTENT, CONTENT),
        END_TERRAIN(Group.CONTENT, CONTENT),
        OCEAN_TERRAIN(Group.CONTENT, CONTENT),
        NETHER_TERRAIN(Group.CONTENT, CONTENT),
        END_MATERIALS(Group.CONTENT, CONTENT),
        LIRESTONE_CRAGS(Group.CONTENT, END_MATERIALS),
        END_WILDS(Group.CONTENT, END_MATERIALS),
        LIRESTONE_BOULDERS(Group.CONTENT, CONTENT),
        WILDS_BOULDERS(Group.CONTENT, END_WILDS),
        LYREN_PLANTS(Group.CONTENT, END_WILDS),
        CHORUS_FLOWERS(Group.CONTENT, CONTENT),
        CHORUS_CROWNS(Group.CONTENT, CHORUS_FLOWERS),
        CHORUS_SPROUTS(Group.CONTENT, CHORUS_FLOWERS),
        DISTORTION(Group.CONTENT, CONTENT),
        CONFUSION(Group.CONTENT, CONTENT),
        STARFALL(Group.CONTENT, CONTENT),
        VILLAGER_WORK(Group.GAMEPLAY, CONTENT),
        FARMER_WORK(Group.GAMEPLAY, VILLAGER_WORK),
        FISHERMAN_WORK(Group.GAMEPLAY, VILLAGER_WORK),
        BUTCHER_WORK(Group.GAMEPLAY, VILLAGER_WORK),
        ANIMAL_CARE(Group.GAMEPLAY, VILLAGER_WORK),
        VILLAGER_BREAD(Group.GAMEPLAY),
        VILLAGER_GATES(Group.GAMEPLAY),
        VILLAGER_SHELTER(Group.GAMEPLAY),
        REPUTATION(Group.GAMEPLAY),
        CROWD_PANIC(Group.GAMEPLAY),
        FISHING_RETURN(Group.GAMEPLAY, CONTENT),
        SAND_PLACEMENT_EVENTS(Group.GAMEPLAY),
        FIREBALL_FIRE_SUPPRESSION(Group.GAMEPLAY),
        GHAST_PHYSICS(Group.GAMEPLAY),
        FISH_MOVEMENT(Group.GAMEPLAY),
        NAVIGATION_INSTRUMENTS(Group.GAMEPLAY),
        ARROW_IMPACT_EVENTS(Group.GAMEPLAY),
        FIREBALL_IMPACT_EVENTS(Group.GAMEPLAY),
        WEATHER_CYCLES(Group.GAMEPLAY),
        END_WEATHER(Group.GAMEPLAY),
        WIND_PHYSICS(Group.GAMEPLAY),
        ELYTRA_MOMENTUM(Group.GAMEPLAY),
        SNOW_WORLDGEN(Group.GAMEPLAY),
        UNDERGROUND_LIGHTNING(Group.GAMEPLAY),
        SKY_EVENT_CYCLES(Group.GAMEPLAY),
        EVENT_CYCLES(Group.GAMEPLAY),
        VISUAL_OVERHAUL(Group.VISUAL),
        RESOURCE_OVERRIDES(Group.VISUAL, VISUAL_OVERHAUL),
        END_SKY(Group.VISUAL, VISUAL_OVERHAUL),
        SKY_EVENTS(Group.VISUAL, VISUAL_OVERHAUL),
        METEORS(Group.VISUAL, SKY_EVENTS),
        PROXIMITY(Group.VISUAL, SKY_EVENTS),
        BLOOD_MOON(Group.VISUAL),
        RAINBOWS(Group.VISUAL, SKY_EVENTS),
        AURORAS(Group.VISUAL, SKY_EVENTS),
        END_FLASHES(Group.VISUAL, SKY_EVENTS),
        RISE_PARTICLES(Group.VISUAL, VISUAL_OVERHAUL),
        END_AMBIENCE(Group.VISUAL, VISUAL_OVERHAUL),
        WEATHER_AUDIO(Group.VISUAL, VISUAL_OVERHAUL),
        UNDERWATER_AUDIO(Group.VISUAL, VISUAL_OVERHAUL),
        LIGHTNING(Group.VISUAL, VISUAL_OVERHAUL),
        PIXEL_BREAKING(Group.VISUAL, VISUAL_OVERHAUL),
        SAND_PARTICLES(Group.VISUAL, VISUAL_OVERHAUL),
        PIXEL_SHADOWS(Group.VISUAL, VISUAL_OVERHAUL),
        PHYSICS_ROPES(Group.VISUAL, VISUAL_OVERHAUL),
        FISHING_MODELS(Group.VISUAL, VISUAL_OVERHAUL),
        MOB_ARROWS(Group.VISUAL, VISUAL_OVERHAUL),
        FIREBALL_MODELS(Group.VISUAL, VISUAL_OVERHAUL),
        GHAST_SQUASH(Group.VISUAL, VISUAL_OVERHAUL),
        MOB_DEATH_PIXELS(Group.VISUAL, VISUAL_OVERHAUL),
        DEATH_SMOKE(Group.VISUAL, VISUAL_OVERHAUL),
        EXPLOSION_PARTICLES(Group.VISUAL, VISUAL_OVERHAUL),
        ELYTRA_CAPE(Group.VISUAL, VISUAL_OVERHAUL),
        ENTITY_FIRE(Group.VISUAL, VISUAL_OVERHAUL),
        FLUID_TAILS(Group.VISUAL, VISUAL_OVERHAUL);

        public enum Group { CONTENT, GAMEPLAY, VISUAL }
        public final Group group;
        public final @org.jspecify.annotations.Nullable Feature parent;
        Feature(Group group) { this(group, null); }
        Feature(Group group, @org.jspecify.annotations.Nullable Feature parent) { this.group = group; this.parent = parent; }
        public String key() { return name().toLowerCase(java.util.Locale.ROOT); }
        // Temporarily suspend custom Nether shapes, including terraced lava, while
        // preserving the saved choice and the independent CONTENT decorations.
        public boolean available() { return this != NETHER_TERRAIN; }
        public boolean enabled() { return FeatureConfig.active(this); }
    }

    /** Filters only resources supplied by Naturality; other mods and user packs are untouched. */
    public static final class FeatureResources {
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
            // These models express mandatory block-state behavior, independently of optional visual overrides.
            if (path.equals("blockstates/cauldron.json") || path.equals("blockstates/water_cauldron.json")
                    || path.equals("blockstates/lava_cauldron.json") || path.equals("blockstates/powder_snow_cauldron.json")) return true;
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
}
