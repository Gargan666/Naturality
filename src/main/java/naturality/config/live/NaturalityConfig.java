package naturality.config.live;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.LoggerFactory;
import java.util.List;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.LivingEntity;
import naturality.config.startup.FeatureConfig.Feature;
import naturality.config.startup.FeatureConfig;
import naturality.config.gameplay.GameplaySettings.NaturalityServerConfig;

@org.jspecify.annotations.NullMarked
public final class NaturalityConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("naturality.json");
    private static final NaturalityConfig INSTANCE = load();
    public boolean epilepsyMode = false;
    public boolean epilepsyWarningAcknowledged = false;
    public Effects effects = new Effects();
    public static final class Effects {
        public boolean sunBeams = true;
        public boolean cameraFlare = true;
        public int hotbarOffset = 3;
        public boolean elasticHands = true;
        public int elasticHandsSpeed = 100;
        public int elasticHandsStrength = 100;
        public int cameraFlareMaxOpacity = 14;
        public int glassShineOpacity = 100;
        public boolean starPulses = true;
        public int auroraSegments = 2;
        public boolean smoke = true;
        public boolean flames = true;
        public boolean leaves = true;
        public int leafRestTicks = 40;
        public boolean fireAnimation = true;
        public boolean connectedFire = true;
        public boolean floatingLeaves = true;
        public boolean rainSplashFade = true;
        public boolean bubblePops = true;
        public boolean fixedBubbleScale = true;
        public boolean snowOverlays = true;
        public boolean weatherParticles = true;
        public boolean foliageWind = true;
        public boolean distantWind = true;
        public boolean distantLeaves = true;
        public boolean distantTextures = true;
        public boolean distantDetails = true;
        public boolean particleWind = true;
        public boolean windSounds = true;
        public boolean pixelGlint = true;
        public boolean worldGlintContours = true;
        public boolean handGlintContours = true;
        public boolean previewGlintContours = true;
        public boolean inventoryGlintContours = true;
        public void sanitize() { hotbarOffset = Math.clamp(hotbarOffset, 0, 10); elasticHandsSpeed = Math.clamp(elasticHandsSpeed, 25, 300); elasticHandsStrength = Math.clamp(elasticHandsStrength, 0, 200); leafRestTicks = Math.clamp(leafRestTicks, 0, 200); auroraSegments = Math.clamp(auroraSegments, 1, 12); cameraFlareMaxOpacity = Math.clamp(cameraFlareMaxOpacity, 0, 100); glassShineOpacity = Math.clamp(glassShineOpacity, 0, 100); }
    }
    public PortalChanges portalChanges = new PortalChanges();
    public Lighting lighting = new Lighting();
    public Fog fog = new Fog();
    public Clouds clouds = new Clouds();
    public Liquids liquids = new Liquids();
    public DynamicLighting dynamicLighting = new DynamicLighting();
    public HardcoreDarkness hardcoreDarkness = new HardcoreDarkness();

    public static final class HardcoreDarkness {
        public CaveDarkness caves = new CaveDarkness();
        public MoonDarkness moon = new MoonDarkness();

        @SuppressWarnings({"null", "unused"}) // Gson can populate explicit JSON nulls.
        public void sanitize() {
            if (caves == null) caves = new CaveDarkness();
            if (moon == null) moon = new MoonDarkness();
            if (caves.dimensions == null) caves.dimensions = new DarknessDimensions();
            if (moon.dimensions == null) moon.dimensions = new DarknessDimensions();
            caves.ambientPercent = Math.clamp(caves.ambientPercent, 0, 100);
            moon.fullPercent = Math.clamp(moon.fullPercent, 0, 100);
            moon.gibbousPercent = Math.clamp(moon.gibbousPercent, 0, 100);
            moon.quarterPercent = Math.clamp(moon.quarterPercent, 0, 100);
            moon.crescentPercent = Math.clamp(moon.crescentPercent, 0, 100);
            moon.newPercent = Math.clamp(moon.newPercent, 0, 100);
        }
    }

    public static final class CaveDarkness {
        public DarknessDimensions dimensions = new DarknessDimensions();
        public boolean enabled = true;
        public int ambientPercent = 0;
        public boolean darkenFog = true;
    }

    public static final class MoonDarkness {
        public DarknessDimensions dimensions = new DarknessDimensions();
        public boolean enabled = true;
        public int fullPercent = 100;
        public int gibbousPercent = 75;
        public int quarterPercent = 50;
        public int crescentPercent = 25;
        public int newPercent = 0;
        public boolean darkenSky = true;
        public boolean darkenClouds = true;
    }

    public static final class DarknessDimensions {
        public boolean overworld = true;
        public boolean nether = false;
        public boolean end = false;
        public boolean other = false;

        public boolean includes(net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension) {
            if (dimension.equals(net.minecraft.world.level.Level.OVERWORLD)) return overworld;
            if (dimension.equals(net.minecraft.world.level.Level.NETHER)) return nether;
            if (dimension.equals(net.minecraft.world.level.Level.END)) return end;
            return other;
        }
    }

    public static final class DynamicLighting {
        public boolean enabled = true;
        public boolean subBlockPrecision = true;
        public int updateTicks = 1;
        public int brightnessPercent = 100;
        public int sourceRange = 48;
        public int maxSources = 64;
        public boolean heldItems = true;
        public boolean emissiveMobs = true;
        public boolean burningEntities = true;
        public boolean droppedItems = true;
        // Resource packs cannot declare emitted light through emissive pixels.
        // These overrides also accept modded entity IDs; zero disables an entry.
        public java.util.Map<String, Integer> entityLightLevels = defaultEntityLights();

        public static java.util.Map<String, Integer> defaultEntityLights() {
            var levels = new java.util.LinkedHashMap<String, Integer>();
            levels.put("minecraft:blaze", 15);
            levels.put("minecraft:glow_squid", 11);
            levels.put("minecraft:magma_cube", 10);
            levels.put("minecraft:glow_item_frame", 8);
            levels.put("minecraft:allay", 7);
            levels.put("minecraft:vex", 7);
            levels.put("minecraft:warden", 6);
            levels.put("minecraft:enderman", 0);
            levels.put("minecraft:spider", 0);
            levels.put("minecraft:cave_spider", 0);
            levels.put("minecraft:drowned", 3);
            levels.put("minecraft:phantom", 2);
            levels.put("minecraft:breeze", 3);
            levels.put("minecraft:creaking", 6);
            levels.put("minecraft:ender_dragon", 8);
            levels.put("minecraft:fireball", 15);
            levels.put("minecraft:small_fireball", 15);
            levels.put("minecraft:dragon_fireball", 10);
            return levels;
        }

        @SuppressWarnings({"null", "unused"}) // Gson can populate explicit JSON nulls.
        public void sanitize() {
            updateTicks = Math.clamp(updateTicks, 1, 20);
            brightnessPercent = Math.clamp(brightnessPercent, 25, 200);
            sourceRange = Math.clamp(sourceRange, 16, 128);
            maxSources = Math.clamp(maxSources, 8, 128);
            if (entityLightLevels == null) entityLightLevels = defaultEntityLights();
            entityLightLevels.replaceAll((_, value) -> value == null ? 0 : Math.clamp(value, 0, 15));
        }
    }

    public static final class Clouds {
        public boolean enabled = true;
        public CloudLayer base = new CloudLayer();
        public CloudLayer upper = upperDefaults();
        public static CloudLayer upperDefaults() {
            var layer = new CloudLayer();
            layer.enabled = false;
            layer.width = 40;
            layer.thickness = 20;
            layer.heightOffset = 94;
            layer.fadePixels = 8;
            layer.offsetX = 96;
            layer.offsetZ = 48;
            layer.speedPercent = 200;
            return layer;
        }
        @SuppressWarnings({"null", "unused"}) // Gson can populate explicit JSON nulls.
        public void sanitize() {
            if (base == null) base = new CloudLayer();
            if (upper == null) upper = upperDefaults();
            base.sanitize();
            upper.sanitize();
        }
    }

    public static final class CloudLayer {
        public boolean enabled = true;
        // 0: open top, 1: solid volume, 2: flat; independent of the game's quality preset.
        public int style = 0;
        public boolean fadingSides = true;
        public int width = 12;
        public int thickness = 16;
        public int heightOffset = 0;
        public int fadePixels = 16;
        public int opacityPercent = 100;
        public int offsetX = 0;
        public int offsetZ = 0;
        public int speedPercent = 100;
        public void sanitize() {
            style = Math.clamp(style, 0, 2);
            width = Math.clamp(width, 4, 64);
            thickness = Math.clamp(thickness, 1, 32);
            heightOffset = Math.clamp(heightOffset, -256, 256);
            fadePixels = Math.clamp(fadePixels, 2, 32);
            opacityPercent = Math.clamp(opacityPercent, 0, 100);
            offsetX = Math.clamp(offsetX, -512, 512);
            offsetZ = Math.clamp(offsetZ, -512, 512);
            speedPercent = Math.clamp(speedPercent, -200, 200);
        }
    }

    public static final class Liquids {
        public boolean waterParticleTint = true;
        public boolean waterWake = true;
        public boolean waterImpactColumns = true;
        public boolean waterSplashSound = true;
        public boolean emissiveLava = true;
        public boolean lavaRim = true;
        public boolean lavaWake = true;
        public boolean lavaRainRipples = true;
        public boolean lavaDripRipples = true;
        public boolean lavafallParticles = true;
        public boolean lavaImpactColumns = true;
        public boolean waterFallDroplets = true;
        public boolean lavaFallDroplets = true;
        public boolean waterfallParticles = true;
        public boolean waterRim = true;
        public boolean rainRipples = true;
        public boolean dripRipples = true;
        public boolean water = true;
        public boolean lava = true;
        public boolean flowingLava = true;
        public boolean waterDepth = true;
        public boolean waterDistortion = true;
        public boolean waterShimmer = true;
        // Legacy serialized setting; no renderer or settings control consumes it.
        public int waterDarkDepth = 12;
        public int waterPixelSize = 16;
        public int heatWavePower = 50;
        public boolean heatWaves = true;

        public void sanitize() {
            waterDarkDepth = Math.clamp(waterDarkDepth, 12, 96);
            waterPixelSize = Math.clamp(waterPixelSize, 1, 16);
            heatWavePower = Math.clamp(heatWavePower, 0, 200);
        }

        public boolean includes(int sprite) {
            return switch (sprite) {
                case 0, 1 -> water;
                case 2 -> lava;
                case 3 -> lava && flowingLava;
                default -> false;
            };
        }
    }

    public static final class Fog {
        public int densitySteps = 8;
        public int borderStartPercent = 40;
        public int borderFullPercent = 75;
        public boolean enabled = true;
        public int pixelSize = 4;

        public void sanitize() { borderStartPercent = Math.clamp(borderStartPercent, 0, 99); borderFullPercent = Math.clamp(borderFullPercent, borderStartPercent + 1, 100); densitySteps = Math.clamp(densitySteps, 2, 64); pixelSize = Math.clamp(pixelSize, 1, 32); }
    }

    public static final class Lighting {
        public boolean enabled = true;
        public double darkStepPercent = 2.0;
        public double lightStepPercent = 10.0;
        public double aoStepPercent = 0.5;
        public double darkSaturationPercent = 100.0;
        public double saturationStartPercent = 30.0;
        public double saturationFullPercent = 10.0;

        public void sanitize() {
            darkStepPercent = bounded(darkStepPercent, 0.1, 10, 2);
            lightStepPercent = bounded(lightStepPercent, darkStepPercent, 10, 10);
            aoStepPercent = bounded(aoStepPercent, 0.1, 5, 0.5);
            darkSaturationPercent = bounded(darkSaturationPercent, 0, 100, 100);
            saturationStartPercent = bounded(saturationStartPercent, 1, 100, 30);
            saturationFullPercent = bounded(saturationFullPercent, 0, saturationStartPercent - 1, 10);
        }

        private static double bounded(double value, double min, double max, double fallback) {
            return Math.clamp(Double.isFinite(value) ? value : fallback, min, max);
        }
    }

    public static final class PortalChanges {
        public boolean endParallax = true;
        public boolean endWalls = true;
        public boolean endGlow = true;
        public boolean eyeGlow = true;
        public boolean suppressEndSmoke = true;
        public volatile boolean portalBlockChanges = true;
        public volatile boolean portalParticleChanges = true;
        public volatile boolean glowEffect = true;
        public volatile double openingVolume = 1.0;
        public volatile double ambientVolume = 0.5485651214128036;
        public volatile double travelVolume = 1.0;
    }

    public static double soundVolume(double value) {
        return Double.isFinite(value) ? Math.clamp(value, 0.0, 1.0) : 1.0;
    }

    public static NaturalityConfig get() { return INSTANCE; }

    public void reset() {
        effects = new Effects();
        portalChanges = new PortalChanges();
        lighting = new Lighting();
        fog = new Fog();
        clouds = new Clouds();
        liquids = new Liquids();
        dynamicLighting = new DynamicLighting();
        hardcoreDarkness = new HardcoreDarkness();
    }

    /** Every visual boolean is also individually editable in the menu. Numeric defaults are retained. */
    public void disableVisuals() {
        disableBooleans(effects);
        disableBooleans(portalChanges);
        disableBooleans(lighting);
        disableBooleans(fog);
        disableBooleans(clouds);
        disableBooleans(clouds.base);
        disableBooleans(clouds.upper);
        disableBooleans(liquids);
        disableBooleans(dynamicLighting);
        disableBooleans(hardcoreDarkness.caves);
        disableBooleans(hardcoreDarkness.moon);
        portalChanges.openingVolume = portalChanges.ambientVolume = portalChanges.travelVolume = 0;
    }

    private static void disableBooleans(Object settings) {
        for (var field : settings.getClass().getFields()) if (field.getType() == boolean.class) {
            try { field.setBoolean(settings, false); }
            catch (IllegalAccessException error) { throw new IllegalStateException(error); }
        }
    }

    @SuppressWarnings({"null", "unused"}) // Validate values populated reflectively by Gson.
    private static NaturalityConfig load() {
        if (Files.exists(FILE)) {
            try (var reader = Files.newBufferedReader(FILE)) {
                NaturalityConfig config = GSON.fromJson(reader, NaturalityConfig.class);
                if (config != null) {
                    if (config.effects == null) config.effects = new Effects();
                    config.effects.sanitize();
                    if (config.portalChanges == null) config.portalChanges = new PortalChanges();
                    if (config.lighting == null) config.lighting = new Lighting();
                    config.lighting.sanitize();
                    if (config.fog == null) config.fog = new Fog();
                    config.fog.sanitize();
                    if (config.clouds == null) config.clouds = new Clouds();
                    if (config.liquids == null) config.liquids = new Liquids();
                    config.liquids.sanitize();
                    config.clouds.sanitize();
                    if (config.dynamicLighting == null) config.dynamicLighting = new DynamicLighting();
                    config.dynamicLighting.sanitize();
                    if (config.hardcoreDarkness == null) config.hardcoreDarkness = new HardcoreDarkness();
                    config.hardcoreDarkness.sanitize();
                    return config;
                }
            } catch (Exception e) {
                LoggerFactory.getLogger("naturality").error("Could not read config; using defaults", e);
            }
            return new NaturalityConfig();
        }
        NaturalityConfig config = new NaturalityConfig();
        config.save();
        return config;
    }

    @SuppressWarnings({"null", "unused"}) // Validate values populated reflectively by Gson.
    public void save() {
        if (effects == null) effects = new Effects();
        effects.sanitize();
        if (liquids == null) liquids = new Liquids();
        liquids.sanitize();
        lighting.sanitize();
        fog.sanitize();
        clouds.sanitize();
        dynamicLighting.sanitize();
        if (hardcoreDarkness == null) hardcoreDarkness = new HardcoreDarkness();
        hardcoreDarkness.sanitize();
        try {
            Files.createDirectories(FILE.getParent());
            Path temp = Files.createTempFile(FILE.getParent(), "naturality-", ".tmp");
            try {
                Files.writeString(temp, GSON.toJson(this) + System.lineSeparator());
                Files.move(temp, FILE, StandardCopyOption.REPLACE_EXISTING);
            } finally { Files.deleteIfExists(temp); }
        } catch (Exception e) {
            LoggerFactory.getLogger("naturality").error("Could not save config", e);
        }
    }

    /** Shared list keeps happy-ghast rider offsets in sync with body lean. Restart to reload. */
    public static final class FloatingMobTiltConfig {
        public List<String> entities=List.of("minecraft:ghast","minecraft:happy_ghast","minecraft:vex","minecraft:allay","minecraft:breeze","minecraft:blaze","minecraft:parrot");
        private static @org.jspecify.annotations.Nullable FloatingMobTiltConfig instance;
        private static Set<String> ids = Set.of();
        @SuppressWarnings({"null", "unused"}) // Gson can violate contracts; keep defensive JSON checks despite static dead-code inference.
        public static FloatingMobTiltConfig get() {
            if(instance!=null) return instance;
            var config=new FloatingMobTiltConfig();
            var file=FabricLoader.getInstance().getConfigDir().resolve("naturality-floating-mob-tilt.json");
            var gson=new GsonBuilder().setPrettyPrinting().create();
            try {
                if(Files.exists(file)) {
                    try(var reader=Files.newBufferedReader(file)) {
                        var loaded=gson.fromJson(reader,FloatingMobTiltConfig.class);
                        if(loaded==null || loaded.entities==null)
                            throw new IllegalArgumentException("entities must be an array of entity IDs");
                        for (@org.jspecify.annotations.Nullable String id : loaded.entities)
                            if (id == null) throw new IllegalArgumentException("entities must be an array of entity IDs");
                        config=loaded;
                    }
                } else {
                    Files.createDirectories(file.getParent()); Files.writeString(file,gson.toJson(config)+"\n");
                }
            } catch(Exception error) {
                LoggerFactory.getLogger("naturality").error("Could not load floating mob tilt list; using defaults",error);
            }
            ids=Set.copyOf(config.entities); instance=config; return instance;
        }
        public static boolean enabled(LivingEntity entity) {
            get(); return ids.contains(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString());
        }
    }

    /** Presets reset ordinary choices and explicitly select the restart-only protocol boundary. */
    public enum ConfigPreset {
        DEFAULT, CLIENTSIDE_ONLY, NO_VISUAL_OVERHAUL;

        public void apply(NaturalityConfig client, NaturalityServerConfig server, FeatureConfig features) {
            client.reset();
            server.reset();
            features.clientOnly = this == CLIENTSIDE_ONLY;
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
}
