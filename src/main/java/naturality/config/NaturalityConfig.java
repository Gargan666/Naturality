package naturality.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.LoggerFactory;

public final class NaturalityConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("naturality.json");
    private static final NaturalityConfig INSTANCE = load();
    public Effects effects = new Effects();
    public static final class Effects {
        public boolean sunBeams = true;
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
        public boolean particleWind = true;
        public boolean windSounds = true;
        public boolean pixelGlint = true;
        public boolean worldGlintContours = true;
        public boolean handGlintContours = true;
        public boolean previewGlintContours = true;
        public boolean inventoryGlintContours = true;
        public void sanitize() { leafRestTicks = Math.clamp(leafRestTicks, 0, 200); auroraSegments = Math.clamp(auroraSegments, 1, 12); }
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
        public int updateTicks = 2;
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
            levels.put("minecraft:enderman", 3);
            levels.put("minecraft:spider", 2);
            levels.put("minecraft:cave_spider", 2);
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
            layer.heightOffset = 40;
            layer.offsetX = 96;
            layer.offsetZ = 48;
            layer.speedPercent = 75;
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
        public int thickness = 4;
        public int heightOffset = 0;
        public int fadePixels = 8;
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
        public int waterDarkDepth = 32;
        public int waterPixelSize = 4;

        public void sanitize() {
            waterDarkDepth = Math.clamp(waterDarkDepth, 12, 96);
            waterPixelSize = Math.clamp(waterPixelSize, 1, 16);
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
        public volatile double ambientVolume = 1.0;
        public volatile double travelVolume = 1.0;
    }

    public static double soundVolume(double value) {
        return Double.isFinite(value) ? Math.clamp(value, 0.0, 1.0) : 1.0;
    }

    public static NaturalityConfig get() { return INSTANCE; }

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
}

