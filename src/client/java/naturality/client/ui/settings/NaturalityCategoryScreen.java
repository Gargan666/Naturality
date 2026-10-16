package naturality.client.ui.settings;

import naturality.client.config.shaders.FeatureShaderSettings;
import naturality.client.config.shaders.FeatureShaderSettings.FogShaderSettings;
import naturality.client.config.shaders.FeatureShaderSettings.LightingShaderSettings;
import naturality.config.live.NaturalityConfig;
import naturality.config.gameplay.GameplaySettings.NaturalityServerConfig;
import naturality.config.startup.FeatureConfig.Feature;
import naturality.config.startup.FeatureConfig;
import naturality.config.live.NaturalityConfig.ConfigPreset;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.network.chat.Component;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import java.util.Map;
import net.minecraft.ChatFormatting;

@org.jspecify.annotations.NullMarked
public class NaturalityCategoryScreen extends OptionsSubScreen {
    public enum Category {
        ROOT(null), PRESETS(ROOT), VISUALS(ROOT),
        LIGHTING(VISUALS), DYNAMIC_LIGHTING(LIGHTING), DARKNESS(LIGHTING),
        CAVE_DARKNESS(DARKNESS), MOON_DARKNESS(DARKNESS),
        ATMOSPHERE(VISUALS), FOG(ATMOSPHERE), CLOUDS(ATMOSPHERE), SKY(ATMOSPHERE), SUN(SKY), WEATHER(ATMOSPHERE),
        LIQUIDS(VISUALS), WATER(LIQUIDS), LAVA(LIQUIDS), PORTAL(VISUALS), FIRE(VISUALS), SNOW(VISUALS),
        PARTICLES(VISUALS), ENTITIES(VISUALS), GLINT(VISUALS),
        CONTENT(ROOT), END_WORLDGEN(CONTENT), CHORUS(CONTENT), CONTENT_EFFECTS(CONTENT),
        GAMEPLAY(ROOT), VILLAGERS(GAMEPLAY), MOB_GAMEPLAY(GAMEPLAY), WEATHER_RULES(GAMEPLAY),
        TRAVEL_RULES(GAMEPLAY), BLOCK_RULES(GAMEPLAY), AUDIO(ROOT);

        public final @org.jspecify.annotations.Nullable Category parent;
        Category(@org.jspecify.annotations.Nullable Category parent) { this.parent = parent; }
        public Category[] children() {
            return java.util.Arrays.stream(values()).filter(child -> child.parent == this).toArray(Category[]::new);
        }
    }
    private final Category category;
    private Category buildingCategory;
    private String query = "";
    private ConfigSearch searchQuery = ConfigSearch.parse("");
    private double previousScroll;
    private double savedScroll;
    private boolean searchHeader;
    private boolean searchLabelsOnly;
    private int matches;
    private String initialFeatures = FeatureShaderSettings.source();
    private boolean initialConnectedFire = NaturalityConfig.get().effects.connectedFire;
    private boolean initialBlocks = NaturalityConfig.get().portalChanges.portalBlockChanges;
    private boolean initialSnowOverlays = NaturalityConfig.get().effects.snowOverlays;
    private boolean applied;
    private naturality.config.gameplay.GameplaySettings.GameplaySettingsPayload initialGameplay = naturality.config.gameplay.GameplaySettings.serverSettings();
    private boolean initialIntro = NaturalityServerConfig.get().portalIntroAnimation;
    private String initialLighting = LightingShaderSettings.source();
    private String initialFog = FogShaderSettings.source();

    public NaturalityCategoryScreen(Screen parent, Category category) {
        super(parent, Minecraft.getInstance().options, Component.translatable(
            "naturality.config.category." + category.name().toLowerCase(java.util.Locale.ROOT)));
        this.category = category;
        this.buildingCategory = category;
    }

    @Override
    protected void init() {
        if (list != null) {
            optionsList().applyUnsavedChanges();
            savedScroll = optionsList().scrollAmount();
        }
        if (applied) {
            initialSnowOverlays = NaturalityConfig.get().effects.snowOverlays;
            initialFeatures = FeatureShaderSettings.source();
            initialLighting = LightingShaderSettings.source();
            initialFog = FogShaderSettings.source();
            initialBlocks = NaturalityConfig.get().portalChanges.portalBlockChanges;
            initialConnectedFire = NaturalityConfig.get().effects.connectedFire;
            initialGameplay = naturality.config.gameplay.GameplaySettings.serverSettings();
            initialIntro = NaturalityServerConfig.get().portalIntroAnimation;
        }
        applied = false;
        layout.removeChildren();
        super.init();
        optionsList().setScrollAmount(savedScroll);
    }

    @Override
    protected void addTitle() {
        layout.setHeaderHeight(64);
        var header = layout.addToHeader(net.minecraft.client.gui.layouts.LinearLayout.vertical().spacing(6));
        header.addChild(new net.minecraft.client.gui.components.StringWidget(title, font),
            settings -> settings.alignHorizontallyCenter());
        var search = header.addChild(new net.minecraft.client.gui.components.EditBox(font, 0, 0, 310, 20,
            Component.translatable("naturality.config.search")));
        search.setHint(Component.translatable("naturality.config.search"));
        search.setTooltip(Tooltip.create(Component.translatable("naturality.config.search.tooltip")));
        search.setMaxLength(128);
        search.setValue(query);
        search.setResponder(value -> {
            if (list == null) return;
            optionsList().applyUnsavedChanges();
            if (query.isBlank() && !value.isBlank()) previousScroll = optionsList().scrollAmount();
            query = value;
            optionsList().replaceEntries(java.util.List.of());
            optionsList().setFocused(null);
            addOptions();
            optionsList().setScrollAmount(query.isBlank() ? previousScroll : 0);
        });
    }

    private static Component categoryTitle(Category category) {
        return Component.translatable("naturality.config.category." + category.name().toLowerCase(java.util.Locale.ROOT));
    }

    private static Component categoryPath(Category category) {
        var parent = category.parent;
        if (parent == null || parent == Category.ROOT) return categoryTitle(category);
        return categoryPath(parent).copy().append(" > ").append(categoryTitle(category));
    }

    private void navigation(Category... categories) {
        for (var child : categories) optionsList().addBig(Button.builder(categoryTitle(child), _ -> {
            optionsList().applyUnsavedChanges();
            savedScroll = optionsList().scrollAmount();
            minecraft.gui.setScreen(new NaturalityCategoryScreen(this, child));
        }).tooltip(Tooltip.create(categoryPath(child))).build());
    }

    private static boolean isPage(Category value) {
        return value != Category.ROOT && value != Category.PRESETS;
    }

    @Override
    protected void addOptions() {
        if (!query.isBlank()) {
            matches = 0;
            searchQuery = ConfigSearch.parse(query);
            // Prefer option-name matches for phrases; category words should not make
            // "End Wilds biome" also match every boulder on an End-biome page.
            searchLabelsOnly = searchQuery.words().size() > 1;
            addSearchOptions();
            if (matches == 0 && searchLabelsOnly) {
                searchLabelsOnly = false;
                addSearchOptions();
            }
            if (matches == 0) optionsList().addHeader(Component.translatable("naturality.config.search.empty"));
            return;
        }
        buildingCategory = category;
        // Main switches precede their subpages on overview pages.
        boolean overview = category == Category.VISUALS || category == Category.CONTENT || category == Category.LIQUIDS;
        if(category==Category.ROOT)toggle("epilepsyMode",NaturalityConfig.get().epilepsyMode,false,v->NaturalityConfig.get().epilepsyMode=v);
        if (!overview) navigation(category.children());
        addCategoryOptions(category);
        if (overview) navigation(category.children());
    }

    private void addSearchOptions() {
        buildingCategory=Category.ROOT;toggle("epilepsyMode",NaturalityConfig.get().epilepsyMode,false,v->NaturalityConfig.get().epilepsyMode=v);
        for (var page : Category.values()) if (isPage(page)) {
            buildingCategory = page;
            searchHeader = false;
            addCategoryOptions(page);
        }
    }

    private void addCategoryOptions(Category category) {
        if (category == Category.PRESETS) {
            optionsList().addHeader(Component.translatable("naturality.config.presets.description"));
            for (var preset : ConfigPreset.values()) {
                String key = "naturality.config.preset." + preset.name().toLowerCase(java.util.Locale.ROOT);
                var button = Button.builder(Component.translatable(key), _ -> {
                    optionsList().applyUnsavedChanges();
                    preset.apply(NaturalityConfig.get(), NaturalityServerConfig.get(), FeatureConfig.get());
                    NaturalityConfig.get().save();
                    NaturalityServerConfig.get().save();
                    FeatureConfig.get().save();
                    optionsList().replaceEntries(java.util.List.of());
                    addOptions();
                }).tooltip(Tooltip.create(Component.translatable(key + ".tooltip"))).build();
                // Registry/state-table changes require a fresh process. Apply from the title menu.
                button.active = minecraft.level == null;
                optionsList().addBig(button);
            }
            optionsList().addHeader(Component.translatable(FeatureConfig.get().restartRequired()
                ? "naturality.config.restart.required" : "naturality.config.presets.editable"));
            if (minecraft.level != null) optionsList().addHeader(Component.translatable("naturality.config.presets.titleOnly"));
            return;
        }
        addFeatureOptions(category);
        if (category == Category.VISUALS) {
            var c = NaturalityConfig.get().effects;
            toggle("effects.elasticHands", c.elasticHands, true, v -> c.elasticHands = v);
            number("effects.elasticHandsSpeed", c.elasticHandsSpeed, 100, 25, 300, v -> c.elasticHandsSpeed = v);
            number("effects.elasticHandsStrength", c.elasticHandsStrength, 100, 0, 200, v -> c.elasticHandsStrength = v);
            toggle("effects.distantWind", c.distantWind, true, v -> c.distantWind = v);
            toggle("effects.distantLeaves", c.distantLeaves, true, v -> c.distantLeaves = v);
            toggle("effects.distantTextures", c.distantTextures, true, v -> c.distantTextures = v);
            toggle("effects.distantDetails", c.distantDetails, true, v -> c.distantDetails = v);
            return;
        }
        if (category == Category.WEATHER) {
            var c = NaturalityConfig.get().effects;
            toggle("effects.rainSplashFade", c.rainSplashFade, true, v -> c.rainSplashFade = v);
            toggle("effects.weatherParticles", c.weatherParticles, true, v -> c.weatherParticles = v);
            toggle("effects.foliageWind", c.foliageWind, true, v -> c.foliageWind = v);
            toggle("effects.particleWind", c.particleWind, true, v -> c.particleWind = v);
            return;
        }
        if (category == Category.SNOW) {
            var c = NaturalityConfig.get().effects;
            toggle("effects.snowOverlays", c.snowOverlays, true, v -> c.snowOverlays = v);
            return;
        }
        if (category == Category.GLINT) {
            var c = NaturalityConfig.get().effects;
            toggle("effects.pixelGlint", c.pixelGlint, true, v -> c.pixelGlint = v);
            toggle("effects.worldGlintContours", c.worldGlintContours, true, v -> c.worldGlintContours = v);
            toggle("effects.handGlintContours", c.handGlintContours, true, v -> c.handGlintContours = v);
            toggle("effects.previewGlintContours", c.previewGlintContours, true, v -> c.previewGlintContours = v);
            toggle("effects.inventoryGlintContours", c.inventoryGlintContours, true, v -> c.inventoryGlintContours = v);
            return;
        }
        if (category == Category.SKY) {
            var c = NaturalityConfig.get().effects;
            toggle("effects.starPulses", c.starPulses, true, v -> c.starPulses = v);
            number("effects.auroraSegments", c.auroraSegments, 2, 1, 12, v -> c.auroraSegments = v);
            return;
        }
        if (category == Category.SUN) {
            var c = NaturalityConfig.get().effects;
            toggle("effects.sunBeams", c.sunBeams, true, v -> c.sunBeams = v);
            toggle("effects.cameraFlare", c.cameraFlare, true, v -> c.cameraFlare = v);
            String label = "naturality.config.effects.cameraFlareMaxOpacity";
            addReset(new OptionInstance<>(label,
                OptionInstance.cachedConstantTooltip(Component.translatable(label + ".tooltip")),
                (caption, amount) -> Component.literal(caption.getString() + ": " + amount + "%"),
                new OptionInstance.IntRange(0, 100), c.cameraFlareMaxOpacity,
                value -> c.cameraFlareMaxOpacity = value), 14);
            return;
        }
        if (category == Category.PARTICLES) {
            var c = NaturalityConfig.get().effects;
            toggle("effects.floatingLeaves", c.floatingLeaves, true, v -> c.floatingLeaves = v);
            toggle("effects.bubblePops", c.bubblePops, true, v -> c.bubblePops = v);
            toggle("effects.fixedBubbleScale", c.fixedBubbleScale, true, v -> c.fixedBubbleScale = v);
            toggle("effects.smoke", c.smoke, true, v -> c.smoke = v);
            toggle("effects.flames", c.flames, true, v -> c.flames = v);
            toggle("effects.leaves", c.leaves, true, v -> c.leaves = v);
            number("effects.leafRestTicks", c.leafRestTicks, 40, 0, 200, v -> c.leafRestTicks = v);
            return;
        }
        if (category == Category.FIRE) {
            var c = NaturalityConfig.get().effects;
            toggle("effects.fireAnimation", c.fireAnimation, true, v -> c.fireAnimation = v);
            toggle("effects.connectedFire", c.connectedFire, true, v -> c.connectedFire = v);
            var liquids = NaturalityConfig.get().liquids;
            var defaults = new NaturalityConfig.Liquids();
            section("heat");
            liquidToggle("heatWaves", liquids.heatWaves, defaults.heatWaves, value -> liquids.heatWaves = value);
            addReset(new OptionInstance<>("naturality.config.liquids.heatWavePower",
                OptionInstance.cachedConstantTooltip(Component.translatable("naturality.config.liquids.heatWavePower.tooltip")),
                (caption, value) -> Component.literal(caption.getString() + ": " + value + "%"),
                new OptionInstance.IntRange(0, 200), liquids.heatWavePower, value -> liquids.heatWavePower = value), defaults.heatWavePower);
            return;
        }
        if (category == Category.BLOCK_RULES) {
            section("sleep");
            var sleep = NaturalityServerConfig.get();
            serverToggle("sleepAcceleration", sleep.sleepAcceleration, true, v -> sleep.sleepAcceleration = v);
            serverNumber("sleepMaxSpeed", sleep.sleepMaxSpeed, 200, 1, 1000, v -> sleep.sleepMaxSpeed = v);
            serverNumber("sleepRampSeconds", sleep.sleepRampSeconds, 30, 1, 120, v -> sleep.sleepRampSeconds = v);
            section("server_rules");
            serverToggle("snowWrapping", NaturalityServerConfig.get().snowWrapping, true, v -> NaturalityServerConfig.get().snowWrapping = v);
            serverToggle("snowCompaction", NaturalityServerConfig.get().snowCompaction, true, v -> NaturalityServerConfig.get().snowCompaction = v);
            serverToggle("fireWrapping", NaturalityServerConfig.get().fireWrapping, true, v -> NaturalityServerConfig.get().fireWrapping = v);
            return;
        }
        if (category == Category.WEATHER_RULES) {
            section("server_rules");
            serverToggle("weatherThaw", NaturalityServerConfig.get().weatherThaw, true, v -> NaturalityServerConfig.get().weatherThaw = v);
            serverToggle("weatherSnowAccumulation", NaturalityServerConfig.get().weatherSnowAccumulation, true, v -> NaturalityServerConfig.get().weatherSnowAccumulation = v);
            return;
        }
        if (category == Category.TRAVEL_RULES) {
            section("server_rules");
            addIntro();
            serverToggle("vanillaPortalEntry", NaturalityServerConfig.get().vanillaPortalEntry, false,
                v -> NaturalityServerConfig.get().vanillaPortalEntry = v);
            return;
        }
        if (category == Category.AUDIO) {
            var effects = NaturalityConfig.get().effects;
            var liquids = NaturalityConfig.get().liquids;
            var portals = NaturalityConfig.get().portalChanges;
            var defaults = new NaturalityConfig.PortalChanges();
            toggle("effects.windSounds", effects.windSounds, true, v -> effects.windSounds = v);
            liquidToggle("waterSplashSound", liquids.waterSplashSound, true, v -> liquids.waterSplashSound = v);
            section("portal_audio");
            addReset(volume("openingVolume", portals.openingVolume, value -> portals.openingVolume = value), defaults.openingVolume);
            addReset(volume("ambientVolume", portals.ambientVolume, value -> portals.ambientVolume = value), defaults.ambientVolume);
            addReset(volume("travelVolume", portals.travelVolume, value -> portals.travelVolume = value), defaults.travelVolume);
            return;
        }
        if (category == Category.WATER) {
            var config = NaturalityConfig.get().liquids;
            var defaults = new NaturalityConfig.Liquids();
            section("surface");
            liquidToggle("water", config.water, defaults.water, value -> config.water = value);
            liquidToggle("waterDepth", config.waterDepth, defaults.waterDepth, value -> config.waterDepth = value);
            liquidToggle("waterDistortion", config.waterDistortion, defaults.waterDistortion, value -> config.waterDistortion = value);
            liquidToggle("waterShimmer", config.waterShimmer, defaults.waterShimmer, value -> config.waterShimmer = value);
            section("splashes");
            liquidToggle("waterParticleTint", config.waterParticleTint, defaults.waterParticleTint, value -> config.waterParticleTint = value);
            liquidToggle("waterRim", config.waterRim, defaults.waterRim, value -> config.waterRim = value);
            liquidToggle("waterWake", config.waterWake, defaults.waterWake, value -> config.waterWake = value);
            liquidToggle("rainRipples", config.rainRipples, defaults.rainRipples, value -> config.rainRipples = value);
            liquidToggle("dripRipples", config.dripRipples, defaults.dripRipples, value -> config.dripRipples = value);
            liquidToggle("waterfallParticles", config.waterfallParticles, defaults.waterfallParticles, value -> config.waterfallParticles = value);
            liquidToggle("waterFallDroplets", config.waterFallDroplets, defaults.waterFallDroplets, value -> config.waterFallDroplets = value);
            liquidToggle("waterImpactColumns", config.waterImpactColumns, defaults.waterImpactColumns, value -> config.waterImpactColumns = value);
            section("detail");
            addReset(new OptionInstance<>("naturality.config.liquids.waterDarkDepth", OptionInstance.noTooltip(),
                (caption, value) -> Component.literal(caption.getString() + ": " + value + " blocks"),
                new OptionInstance.IntRange(12, 96), config.waterDarkDepth, value -> config.waterDarkDepth = value), defaults.waterDarkDepth);
            addReset(new OptionInstance<>("naturality.config.liquids.waterPixelSize", OptionInstance.noTooltip(),
                (caption, value) -> Component.literal(caption.getString() + ": " + value),
                new OptionInstance.IntRange(1, 16), config.waterPixelSize, value -> config.waterPixelSize = value), defaults.waterPixelSize);
            return;
        }
        if (category == Category.LAVA) {
            var config = NaturalityConfig.get().liquids;
            var defaults = new NaturalityConfig.Liquids();
            section("surface");
            liquidToggle("lava", config.lava, defaults.lava, value -> config.lava = value);
            liquidToggle("flowingLava", config.flowingLava, defaults.flowingLava, value -> config.flowingLava = value);
            liquidToggle("emissiveLava", config.emissiveLava, defaults.emissiveLava, value -> config.emissiveLava = value);
            section("splashes");
            liquidToggle("lavaRim", config.lavaRim, defaults.lavaRim, value -> config.lavaRim = value);
            liquidToggle("lavaWake", config.lavaWake, defaults.lavaWake, value -> config.lavaWake = value);
            liquidToggle("lavaRainRipples", config.lavaRainRipples, defaults.lavaRainRipples, value -> config.lavaRainRipples = value);
            liquidToggle("lavaDripRipples", config.lavaDripRipples, defaults.lavaDripRipples, value -> config.lavaDripRipples = value);
            liquidToggle("lavafallParticles", config.lavafallParticles, defaults.lavafallParticles, value -> config.lavafallParticles = value);
            liquidToggle("lavaFallDroplets", config.lavaFallDroplets, defaults.lavaFallDroplets, value -> config.lavaFallDroplets = value);
            liquidToggle("lavaImpactColumns", config.lavaImpactColumns, defaults.lavaImpactColumns, value -> config.lavaImpactColumns = value);
            return;
        }
        if (category == Category.CAVE_DARKNESS) {
            var config = NaturalityConfig.get().hardcoreDarkness.caves;
            var defaults = new NaturalityConfig.CaveDarkness();
            darknessToggle("caves.enabled", config.enabled, defaults.enabled, value -> config.enabled = value);
            darknessPercent("caves.ambientPercent", config.ambientPercent, defaults.ambientPercent, value -> config.ambientPercent = value);
            darknessToggle("caves.darkenFog", config.darkenFog, defaults.darkenFog, value -> config.darkenFog = value);
            darknessDimensions(config.dimensions);
            return;
        }
        if (category == Category.MOON_DARKNESS) {
            var config = NaturalityConfig.get().hardcoreDarkness.moon;
            var defaults = new NaturalityConfig.MoonDarkness();
            darknessToggle("moon.enabled", config.enabled, defaults.enabled, value -> config.enabled = value);
            darknessPercent("moon.fullPercent", config.fullPercent, defaults.fullPercent, value -> config.fullPercent = value);
            darknessPercent("moon.gibbousPercent", config.gibbousPercent, defaults.gibbousPercent, value -> config.gibbousPercent = value);
            darknessPercent("moon.quarterPercent", config.quarterPercent, defaults.quarterPercent, value -> config.quarterPercent = value);
            darknessPercent("moon.crescentPercent", config.crescentPercent, defaults.crescentPercent, value -> config.crescentPercent = value);
            darknessPercent("moon.newPercent", config.newPercent, defaults.newPercent, value -> config.newPercent = value);
            darknessToggle("moon.darkenSky", config.darkenSky, defaults.darkenSky, value -> config.darkenSky = value);
            darknessToggle("moon.darkenClouds", config.darkenClouds, defaults.darkenClouds, value -> config.darkenClouds = value);
            darknessDimensions(config.dimensions);
            return;
        }
        if (category == Category.CLOUDS) {
            var config = NaturalityConfig.get().clouds;
            cloudToggle("enabled", config.enabled, true, value -> config.enabled = value);
            addCloudLayer("base", config.base, new NaturalityConfig.CloudLayer());
            addCloudLayer("upper", config.upper, NaturalityConfig.Clouds.upperDefaults());
            return;
        }
        if (category == Category.DYNAMIC_LIGHTING) {
            var config = NaturalityConfig.get().dynamicLighting;
            var defaults = new NaturalityConfig.DynamicLighting();
            dynamicToggle("enabled", config.enabled, defaults.enabled, value -> config.enabled = value);
            dynamicToggle("subBlockPrecision", config.subBlockPrecision, defaults.subBlockPrecision, value -> config.subBlockPrecision = value);
            dynamicNumber("updateTicks", config.updateTicks, defaults.updateTicks, 1, 20, value -> config.updateTicks = value);
            dynamicNumber("brightnessPercent", config.brightnessPercent, defaults.brightnessPercent, 25, 200, value -> config.brightnessPercent = value);
            dynamicNumber("sourceRange", config.sourceRange, defaults.sourceRange, 16, 128, value -> config.sourceRange = value);
            dynamicNumber("maxSources", config.maxSources, defaults.maxSources, 8, 128, value -> config.maxSources = value);
            dynamicToggle("heldItems", config.heldItems, defaults.heldItems, value -> config.heldItems = value);
            dynamicToggle("emissiveMobs", config.emissiveMobs, defaults.emissiveMobs, value -> config.emissiveMobs = value);
            dynamicToggle("burningEntities", config.burningEntities, defaults.burningEntities, value -> config.burningEntities = value);
            dynamicToggle("droppedItems", config.droppedItems, defaults.droppedItems, value -> config.droppedItems = value);
            var ids = new java.util.TreeSet<>(config.entityLightLevels.keySet());
            ids.addAll(defaults.entityLightLevels.keySet());
            for (String id : ids) {
                int fallback = defaults.entityLightLevels.getOrDefault(id, 0);
                addReset(new OptionInstance<>("Entity light: " + id, OptionInstance.noTooltip(),
                    (caption, value) -> Component.literal(caption.getString() + ": " + value),
                    new OptionInstance.IntRange(0, 15), config.entityLightLevels.getOrDefault(id, 0),
                    value -> config.entityLightLevels.put(id, value)), fallback);
            }
            return;
        }
        if (category == Category.FOG) {
            var config = NaturalityConfig.get().fog;
            var defaults = new NaturalityConfig.Fog();
            toggle("fog.enabled", config.enabled, defaults.enabled, value -> config.enabled = value);
            addReset(new OptionInstance<>("naturality.config.fog.pixelSize",
                OptionInstance.cachedConstantTooltip(Component.translatable("naturality.config.fog.pixelSize.tooltip")),
                (caption, value) -> Component.literal(caption.getString() + ": " + value + "×" + value),
                new OptionInstance.IntRange(1, 32), config.pixelSize, value -> config.pixelSize = value), defaults.pixelSize);
            number("fog.densitySteps", config.densitySteps, defaults.densitySteps, 2, 64, v -> config.densitySteps = v);
            number("fog.borderStartPercent", config.borderStartPercent, defaults.borderStartPercent, 0, 99, v -> config.borderStartPercent = v);
            number("fog.borderFullPercent", config.borderFullPercent, defaults.borderFullPercent, 1, 100, v -> config.borderFullPercent = v);
            return;
        }
        if (category == Category.LIGHTING) {
            addLightingOptions();
            return;
        }
        if (category != Category.PORTAL) return;
        var config = NaturalityConfig.get().portalChanges;
        var defaults = new NaturalityConfig.PortalChanges();
        section("nether_portal");
        toggle("blocks", config.portalBlockChanges, defaults.portalBlockChanges, value -> config.portalBlockChanges = value);
        toggle("particles", config.portalParticleChanges, defaults.portalParticleChanges, value -> config.portalParticleChanges = value);
        toggle("glow", config.glowEffect, defaults.glowEffect, value -> config.glowEffect = value);
        section("end_portal");
        toggle("portal.endParallax", config.endParallax, defaults.endParallax, v -> config.endParallax = v);
        toggle("portal.endWalls", config.endWalls, defaults.endWalls, v -> config.endWalls = v);
        toggle("portal.endGlow", config.endGlow, defaults.endGlow, v -> config.endGlow = v);
        toggle("portal.eyeGlow", config.eyeGlow, defaults.eyeGlow, v -> config.eyeGlow = v);
        toggle("portal.suppressEndSmoke", config.suppressEndSmoke, defaults.suppressEndSmoke, v -> config.suppressEndSmoke = v);
    }

    private void section(String key) {
        if (query.isBlank()) optionsList().addHeader(Component.translatable("naturality.config.section." + key));
    }

    private void addFeatureOptions(Category page) {
        boolean header = false;
        for (var feature : Feature.values()) if (ConfigLayout.category(feature) == page) {
            if (!header) { section("startup"); header = true; }
            String label = "naturality.config.feature." + feature.key();
            var tooltip = Component.translatable(label + ".tooltip")
                .append(" ").append(Component.translatable("naturality.config.restart.tooltip"));
            var parent = feature.parent;
            if (parent != null) tooltip.append(" ").append(Component.translatable("naturality.config.requires",
                Component.translatable("naturality.config.feature." + parent.key())));
            var option = OptionInstance.createBoolean(label,
                OptionInstance.cachedConstantTooltip(VisualPerformance.tooltip(label, tooltip)),
                FeatureConfig.get().get(feature), value -> FeatureConfig.get().set(feature, value));
            var reset = addReset(option, true, VisualPerformance.estimate("feature." + feature.key()));
            var button = optionsList().findOption(option);
            boolean editable = feature.group == Feature.Group.VISUAL || minecraft.level == null;
            if (button != null) button.active = editable;
            if (reset != null) reset.active = editable;
        }
    }

    private void serverToggle(String key, boolean value, boolean fallback, java.util.function.Consumer<Boolean> update) {
        String label = "naturality.config.gameplay." + key;
        boolean editable = minecraft.level == null || minecraft.hasSingleplayerServer();
        if (!editable) value = switch (key) {
            case "sleepAcceleration" -> naturality.config.gameplay.GameplaySettings.displayedSettings().sleepAcceleration();
            case "fireWrapping" -> naturality.config.gameplay.GameplaySettings.clientFireWrapping();
            case "snowWrapping" -> naturality.config.gameplay.GameplaySettings.clientSnowWrapping();
            case "snowCompaction" -> naturality.config.gameplay.GameplaySettings.clientSnowCompaction();
            case "weatherThaw" -> naturality.config.gameplay.GameplaySettings.clientWeatherThaw();
            case "weatherSnowAccumulation" -> naturality.config.gameplay.GameplaySettings.clientWeatherSnowAccumulation();
            default -> !naturality.config.gameplay.GameplaySettings.clientPhysicalPortalEntry();
        };
        var option = OptionInstance.createBoolean(label,
            OptionInstance.cachedConstantTooltip(Component.translatable(label + ".tooltip")), value, update::accept);
        var reset = addReset(option, fallback);
        var widget = optionsList().findOption(option);
        if (widget != null) widget.active = editable;
        if (reset != null) reset.active = editable;
    }

    private void serverNumber(String key, int value, int fallback, int min, int max, java.util.function.IntConsumer update) {
        boolean editable = minecraft.level == null || minecraft.hasSingleplayerServer();
        if (!editable) {
            var settings = naturality.config.gameplay.GameplaySettings.displayedSettings();
            value = key.equals("sleepMaxSpeed") ? settings.sleepMaxSpeed() : settings.sleepRampSeconds();
        }
        String label = "naturality.config.gameplay." + key;
        var option = new OptionInstance<Integer>(label,
            OptionInstance.cachedConstantTooltip(Component.translatable(label + ".tooltip")),
            (caption, amount) -> Component.literal(caption.getString() + ": " + amount + (key.equals("sleepMaxSpeed") ? "×" : " s")),
            new OptionInstance.IntRange(min, max), value, update::accept);
        var reset = addReset(option, fallback);
        var widget = optionsList().findOption(option);
        if (widget != null) widget.active = editable;
        if (reset != null) reset.active = editable;
    }
    private void addIntro() {
        var introOption = OptionInstance.createBoolean("naturality.config.intro",
            OptionInstance.cachedConstantTooltip(Component.translatable("naturality.config.intro.tooltip")),
            NaturalityServerConfig.get().portalIntroAnimation,
            value -> NaturalityServerConfig.get().portalIntroAnimation = value);
        var introReset = addReset(introOption, new NaturalityServerConfig().portalIntroAnimation);
        // This screen can configure the local host, never a server owned by somebody else.
        var introButton = optionsList().findOption(introOption);
        if (introButton != null) introButton.active = minecraft.level == null || minecraft.hasSingleplayerServer();
        if (introReset != null) introReset.active = minecraft.level == null || minecraft.hasSingleplayerServer();
    }

    private void toggle(String key, boolean value, boolean fallback, java.util.function.Consumer<Boolean> update) {
        String label = "naturality.config." + key;
        addReset(OptionInstance.createBoolean(label,
            OptionInstance.cachedConstantTooltip(VisualPerformance.tooltip(label)), value, update::accept), fallback,
            VisualPerformance.estimate(key));
    }

    private void number(String key, int value, int fallback, int min, int max, java.util.function.IntConsumer update) {
        String label = "naturality.config." + key;
        addReset(new OptionInstance<>(label, OptionInstance.cachedConstantTooltip(Component.translatable(label + ".tooltip")),
            (caption, amount) -> Component.literal(caption.getString() + ": " + amount),
            new OptionInstance.IntRange(min, max), value, update::accept), fallback);
    }

    private void liquidToggle(String key, boolean value, boolean fallback, java.util.function.Consumer<Boolean> update) {
        toggle("liquids." + key, value, fallback, update);
    }

    private void darknessDimensions(NaturalityConfig.DarknessDimensions dimensions) {
        darknessToggle("dimensions.overworld", dimensions.overworld, true, value -> dimensions.overworld = value);
        darknessToggle("dimensions.nether", dimensions.nether, false, value -> dimensions.nether = value);
        darknessToggle("dimensions.end", dimensions.end, false, value -> dimensions.end = value);
        darknessToggle("dimensions.other", dimensions.other, false, value -> dimensions.other = value);
    }

    private void darknessToggle(String key, boolean value, boolean fallback, java.util.function.Consumer<Boolean> update) {
        String label = "naturality.config.hardcoreDarkness." + key;
        addReset(OptionInstance.createBoolean(label,
            OptionInstance.cachedConstantTooltip(Component.translatable(label + ".tooltip")), value, update::accept), fallback);
    }

    private void darknessPercent(String key, int value, int fallback, java.util.function.IntConsumer update) {
        String label = "naturality.config.hardcoreDarkness." + key;
        addReset(new OptionInstance<>(label,
            OptionInstance.cachedConstantTooltip(Component.translatable(label + ".tooltip")),
            (caption, amount) -> Component.literal(caption.getString() + ": " + amount + "%"),
            new OptionInstance.IntRange(0, key.equals("direction") ? 360 : 100), value, update::accept), fallback);
    }

    private void addCloudLayer(String name, NaturalityConfig.CloudLayer layer, NaturalityConfig.CloudLayer defaults) {
        cloudToggle(name + ".enabled", layer.enabled, defaults.enabled, value -> layer.enabled = value);
        String label = "naturality.config.clouds." + name + ".style";
        addReset(new OptionInstance<>(label, OptionInstance.cachedConstantTooltip(Component.translatable(label + ".tooltip")),
            (caption, value) -> Component.literal(caption.getString() + ": ").append(Component.translatable("naturality.config.clouds.style." + value)),
            new OptionInstance.IntRange(0, 2), layer.style, value -> layer.style = value), defaults.style);
        cloudToggle(name + ".fadingSides", layer.fadingSides, defaults.fadingSides, value -> layer.fadingSides = value);
        cloudNumber(name + ".opacityPercent", layer.opacityPercent, defaults.opacityPercent, 0, 100, value -> layer.opacityPercent = value);
        cloudNumber(name + ".width", layer.width, defaults.width, 4, 64, value -> layer.width = value);
        cloudNumber(name + ".thickness", layer.thickness, defaults.thickness, 1, 32, value -> layer.thickness = value);
        cloudNumber(name + ".heightOffset", layer.heightOffset, defaults.heightOffset, -256, 256, value -> layer.heightOffset = value);
        cloudNumber(name + ".fadePixels", layer.fadePixels, defaults.fadePixels, 2, 32, value -> layer.fadePixels = value);
        cloudNumber(name + ".offsetX", layer.offsetX, defaults.offsetX, -512, 512, value -> layer.offsetX = value);
        cloudNumber(name + ".offsetZ", layer.offsetZ, defaults.offsetZ, -512, 512, value -> layer.offsetZ = value);
        cloudNumber(name + ".speedPercent", layer.speedPercent, defaults.speedPercent, -200, 200, value -> layer.speedPercent = value);
    }

    private void cloudToggle(String key, boolean value, boolean fallback, java.util.function.Consumer<Boolean> update) {
        toggle("clouds." + key, value, fallback, update);
    }

    private void cloudNumber(String key, int value, int fallback, int min, int max, java.util.function.IntConsumer update) {
        String label = "naturality.config.clouds." + key;
        addReset(new OptionInstance<>(label, OptionInstance.cachedConstantTooltip(Component.translatable(label + ".tooltip")),
            (caption, amount) -> Component.literal(caption.getString() + ": " + amount + (key.endsWith("Percent") ? "%" : "")),
            new OptionInstance.IntRange(min, max), value, amount -> update.accept(amount)), fallback);
    }

    private void dynamicToggle(String key, boolean value, boolean defaultValue, java.util.function.Consumer<Boolean> update) {
        toggle("dynamicLighting." + key, value, defaultValue, update);
    }

    private void dynamicNumber(String key, int value, int defaultValue, int min, int max, java.util.function.IntConsumer update) {
        String label = "naturality.config.dynamicLighting." + key;
        addReset(new OptionInstance<>(label,
            OptionInstance.cachedConstantTooltip(Component.translatable(label + ".tooltip")),
            (caption, amount) -> Component.literal(caption.getString() + ": " +
                (key.equals("updateTicks") ? amount * 50 + " ms" : amount + (key.equals("brightnessPercent") ? "%" : ""))),
            new OptionInstance.IntRange(min, max), value, amount -> update.accept(amount)), defaultValue);
    }

    private void addLightingOptions() {
        var lighting = NaturalityConfig.get().lighting;
        toggle("lighting.enabled", lighting.enabled, true, v -> lighting.enabled = v);
        var defaults = new NaturalityConfig.Lighting();
        addReset(lighting("darkStep", lighting.darkStepPercent, 0.1, 10, value -> lighting.darkStepPercent = value), normalized(defaults.darkStepPercent, 0.1, 10));
        addReset(lighting("lightStep", lighting.lightStepPercent, 0.1, 10, value -> lighting.lightStepPercent = value), normalized(defaults.lightStepPercent, 0.1, 10));
        addReset(lighting("aoStep", lighting.aoStepPercent, 0.1, 5, value -> lighting.aoStepPercent = value), normalized(defaults.aoStepPercent, 0.1, 5));
        addReset(lighting("darkSaturation", lighting.darkSaturationPercent, 0, 100, value -> lighting.darkSaturationPercent = value), normalized(defaults.darkSaturationPercent, 0, 100));
        addReset(lighting("saturationStart", lighting.saturationStartPercent, 1, 100, value -> lighting.saturationStartPercent = value), normalized(defaults.saturationStartPercent, 1, 100));
        addReset(lighting("saturationFull", lighting.saturationFullPercent, 0, 99, value -> lighting.saturationFullPercent = value), normalized(defaults.saturationFullPercent, 0, 99));
    }

    private <T> @org.jspecify.annotations.Nullable Button addReset(OptionInstance<T> option, T defaultValue) {
        return addReset(option, defaultValue, null);
    }

    private <T> @org.jspecify.annotations.Nullable Button addReset(OptionInstance<T> option, T defaultValue,
            VisualPerformance.@org.jspecify.annotations.Nullable Impact impact) {
        var widget = option.createButton(options, 0, 0, 240);
        if (!query.isBlank()) {
            String haystack = (searchLabelsOnly ? "" : categoryTitle(buildingCategory).getString() + " ") + widget.getMessage().getString();
            if (!searchQuery.matches(haystack, impact)) return null;
            if (!searchHeader) { optionsList().addHeader(categoryPath(buildingCategory)); searchHeader = true; }
            matches++;
        }
        var reset = new Button.Plain(0, 0, 60, 20, Component.translatable("naturality.config.reset"), _ -> {
            option.set(defaultValue);
            // Refresh both toggle text and slider position, cancelling any pending slider value.
            optionsList().resetOption(option);
        }, narration -> narration.get()) {
            @Override
            public void setPosition(int x, int y) {
                // OptionsList places the second column at +160; our wider control needs +250.
                super.setPosition(x + 90, y);
            }
        };
        reset.setTooltip(Tooltip.create(Component.translatable("naturality.config.reset.tooltip")));
        optionsList().addSmall(widget, option, reset);
        return reset;
    }

    private net.minecraft.client.gui.components.OptionsList optionsList() {
        return java.util.Objects.requireNonNull(list, "Options list is initialized before controls are used");
    }

    private static double normalized(double value, double min, double max) {
        return Math.clamp((value - min) / (max - min), 0, 1);
    }

    @Override
    public void removed() {
        if (list != null) { optionsList().applyUnsavedChanges(); savedScroll = optionsList().scrollAmount(); }
        super.removed();
        if (applied) return;
        applied = true;
        NaturalityConfig.get().save();
        FeatureConfig.get().save();
        if (minecraft.level == null || minecraft.hasSingleplayerServer()) NaturalityServerConfig.get().save();
        if (initialIntro != NaturalityServerConfig.get().portalIntroAnimation
                || !initialGameplay.equals(naturality.config.gameplay.GameplaySettings.serverSettings())) NaturalityServerConfig.get().save();
        // Portal emission is baked into materials, so rebuild models as well as chunks.
        if (initialBlocks != NaturalityConfig.get().portalChanges.portalBlockChanges
                || !initialLighting.equals(LightingShaderSettings.source())
                || !initialFog.equals(FogShaderSettings.source())
                || !initialFeatures.equals(FeatureShaderSettings.source())
                || initialConnectedFire != NaturalityConfig.get().effects.connectedFire
                || initialSnowOverlays != NaturalityConfig.get().effects.snowOverlays) {
            minecraft.reloadResourcePacks();
        }
    }

    private static OptionInstance<Double> lighting(String key, double value, double min, double max,
            java.util.function.DoubleConsumer update) {
        String label = "naturality.config.lighting." + key;
        return new OptionInstance<>(label,
            OptionInstance.cachedConstantTooltip(Component.translatable(label + ".tooltip")),
            (caption, amount) -> Component.literal(caption.getString() + ": "
                + String.format(java.util.Locale.ROOT, "%.1f%%", Math.round((min + amount * (max - min)) * 10) / 10.0)),
            OptionInstance.UnitDouble.INSTANCE, Math.clamp((value - min) / (max - min), 0, 1),
            amount -> update.accept(Math.round((min + amount * (max - min)) * 10) / 10.0));
    }

    private static OptionInstance<Double> volume(String key, double value, java.util.function.DoubleConsumer update) {
        return new OptionInstance<>("naturality.config." + key, OptionInstance.noTooltip(),
            (caption, amount) -> Component.literal(caption.getString() + ": " + Math.round(amount * 100) + "%"),
            OptionInstance.UnitDouble.INSTANCE, NaturalityConfig.soundVolume(value), amount -> update.accept(amount));
    }

    /** Mod Menu entry point, sharing navigation, search and saving with every page. */
    public static final class NaturalityConfigScreen extends NaturalityCategoryScreen {
        public NaturalityConfigScreen(Screen parent) { super(parent, Category.ROOT); }
        public NaturalityConfigScreen(Screen parent, boolean darknessCategories) {
            super(parent, darknessCategories ? Category.DARKNESS : Category.ROOT);
        }
    }

    /** One canonical topic for every startup switch, also used by global search. */
    public static final class ConfigLayout {
        private ConfigLayout() {}

        public static Category category(Feature feature) {
            return switch (feature) {
                case CONTENT, MOUNTAIN_WATERFALLS -> Category.CONTENT;
                case END_TERRAIN, END_MATERIALS, LIRESTONE_CRAGS, END_WILDS, LIRESTONE_BOULDERS,
                    WILDS_BOULDERS, LYREN_PLANTS -> Category.END_WORLDGEN;
                case CHORUS_FLOWERS, CHORUS_CROWNS, CHORUS_SPROUTS -> Category.CHORUS;
                case DISTORTION, CONFUSION, STARFALL -> Category.CONTENT_EFFECTS;
                case VILLAGER_WORK, FARMER_WORK, FISHERMAN_WORK, BUTCHER_WORK, ANIMAL_CARE,
                    VILLAGER_BREAD, VILLAGER_GATES, VILLAGER_SHELTER, REPUTATION -> Category.VILLAGERS;
                case CROWD_PANIC, FISHING_RETURN, FIREBALL_FIRE_SUPPRESSION, GHAST_PHYSICS, FISH_MOVEMENT,
                    ARROW_IMPACT_EVENTS, FIREBALL_IMPACT_EVENTS -> Category.MOB_GAMEPLAY;
                case SAND_PLACEMENT_EVENTS, SNOW_WORLDGEN -> Category.BLOCK_RULES;
                case WEATHER_CYCLES, END_WEATHER, UNDERGROUND_LIGHTNING, SKY_EVENT_CYCLES, EVENT_CYCLES -> Category.WEATHER_RULES;
                case WIND_PHYSICS, ELYTRA_MOMENTUM, NAVIGATION_INSTRUMENTS -> Category.TRAVEL_RULES;
                case VISUAL_OVERHAUL, RESOURCE_OVERRIDES -> Category.VISUALS;
                case END_SKY, SKY_EVENTS, METEORS, PROXIMITY, BLOOD_MOON, RAINBOWS, AURORAS, END_FLASHES -> Category.SKY;
                case RISE_PARTICLES, LIGHTNING -> Category.WEATHER;
                case END_AMBIENCE, WEATHER_AUDIO, UNDERWATER_AUDIO -> Category.AUDIO;
                case PIXEL_BREAKING, SAND_PARTICLES, DEATH_SMOKE, EXPLOSION_PARTICLES -> Category.PARTICLES;
                case PIXEL_SHADOWS -> Category.LIGHTING;
                case PHYSICS_ROPES, FISHING_MODELS, MOB_ARROWS, FIREBALL_MODELS, GHAST_SQUASH,
                    MOB_DEATH_PIXELS, ELYTRA_CAPE -> Category.ENTITIES;
                case ENTITY_FIRE -> Category.FIRE;
                case FLUID_TAILS -> Category.LIQUIDS;
            };
        }
    }

    /** Text terms narrow the results; multiple impact tags accept any of the named tiers. */
    record ConfigSearch(List<String> words, Set<VisualPerformance.Impact> impacts, boolean valid) {
        static ConfigSearch parse(String query) {
            var words = new ArrayList<String>();
            var impacts = EnumSet.noneOf(VisualPerformance.Impact.class);
            boolean valid = true;
            var tokens = query.strip().toLowerCase(Locale.ROOT).split("\\s+");
            for (int i = 0; i < tokens.length; i++) {
                String token = tokens[i];
                if (token.isEmpty()) continue;
                if (!token.startsWith("#")) {
                    words.add(token);
                    continue;
                }
                if (token.equals("#extra") && i + 1 < tokens.length && tokens[i + 1].equals("high")) {
                    token = "#extrahigh";
                    i++;
                }
                var impact = switch (token.replace("-", "").replace("_", "")) {
                    case "#low" -> VisualPerformance.Impact.LOW;
                    case "#medium" -> VisualPerformance.Impact.MEDIUM;
                    case "#high" -> VisualPerformance.Impact.HIGH;
                    case "#extrahigh" -> VisualPerformance.Impact.EXTRA_HIGH;
                    default -> null;
                };
                if (impact == null) valid = false;
                else impacts.add(impact);
            }
            return new ConfigSearch(List.copyOf(words), Set.copyOf(impacts), valid);
        }

        boolean matches(String text, VisualPerformance.@Nullable Impact impact) {
            if (!valid || (!impacts.isEmpty() && (impact == null || !impacts.contains(impact)))) return false;
            String normalized = text.toLowerCase(Locale.ROOT);
            return words.stream().allMatch(normalized::contains);
        }
    }

    /** Provisional, qualitative FPS-cost estimates; deliberately not hardware benchmark numbers. */
    public static final class VisualPerformance {
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
}
