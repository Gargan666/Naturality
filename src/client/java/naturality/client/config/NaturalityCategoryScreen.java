package naturality.client.config;
import naturality.config.NaturalityConfig;
import naturality.config.NaturalityServerConfig;

import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.network.chat.Component;

public class NaturalityCategoryScreen extends OptionsSubScreen {
    public enum Category {
        LIGHTING, DYNAMIC_LIGHTING, CAVE_DARKNESS, MOON_DARKNESS, CLOUDS, LIQUIDS,
        FOG, PORTAL, SKY, PARTICLES, FIRE, GAMEPLAY, ROOT, ATMOSPHERE, EFFECTS, DARKNESS, WEATHER, SNOW, GLINT
    }
    private final Category category;
    private Category buildingCategory;
    private String query = "";
    private double previousScroll;
    private double savedScroll;
    private boolean searchHeader;
    private int matches;
    private String initialFeatures = FeatureShaderSettings.source();
    private boolean initialConnectedFire = NaturalityConfig.get().effects.connectedFire;
    private boolean initialBlocks = NaturalityConfig.get().portalChanges.portalBlockChanges;
    private boolean initialSnowOverlays = NaturalityConfig.get().effects.snowOverlays;
    private boolean applied;
    private naturality.config.GameplaySettingsPayload initialGameplay = naturality.config.GameplaySettings.serverSettings();
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
            initialGameplay = naturality.config.GameplaySettings.serverSettings();
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

    private void navigation(Category... categories) {
        for (var child : categories) optionsList().addBig(Button.builder(categoryTitle(child), _ -> {
            optionsList().applyUnsavedChanges();
            savedScroll = optionsList().scrollAmount();
            minecraft.gui.setScreen(new NaturalityCategoryScreen(this, child));
        }).build());
    }

    private static boolean isPage(Category value) {
        return value != Category.ROOT && value != Category.ATMOSPHERE
            && value != Category.EFFECTS && value != Category.DARKNESS;
    }

    @Override
    protected void addOptions() {
        if (!query.isBlank()) {
            matches = 0;
            for (var page : Category.values()) if (isPage(page)) {
                buildingCategory = page;
                searchHeader = false;
                addCategoryOptions(page);
            }
            if (matches == 0) optionsList().addHeader(Component.translatable("naturality.config.search.empty"));
            return;
        }
        buildingCategory = category;
        switch (category) {
            case ROOT -> navigation(Category.ATMOSPHERE, Category.EFFECTS, Category.GAMEPLAY);
            case ATMOSPHERE -> navigation(Category.FOG, Category.LIGHTING, Category.DYNAMIC_LIGHTING,
                Category.DARKNESS, Category.CLOUDS, Category.SKY, Category.WEATHER);
            case EFFECTS -> navigation(Category.PORTAL, Category.LIQUIDS, Category.PARTICLES, Category.FIRE, Category.SNOW, Category.GLINT);
            case DARKNESS -> navigation(Category.CAVE_DARKNESS, Category.MOON_DARKNESS);
            default -> addCategoryOptions(category);
        }
    }

    private void addCategoryOptions(Category category) {
        if (category == Category.WEATHER) {
            var c = NaturalityConfig.get().effects;
            toggle("effects.rainSplashFade", c.rainSplashFade, true, v -> c.rainSplashFade = v);
            toggle("effects.weatherParticles", c.weatherParticles, true, v -> c.weatherParticles = v);
            toggle("effects.foliageWind", c.foliageWind, true, v -> c.foliageWind = v);
            toggle("effects.particleWind", c.particleWind, true, v -> c.particleWind = v);
            toggle("effects.windSounds", c.windSounds, true, v -> c.windSounds = v);
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
            toggle("effects.sunBeams", c.sunBeams, true, v -> c.sunBeams = v);
            toggle("effects.starPulses", c.starPulses, true, v -> c.starPulses = v);
            number("effects.auroraSegments", c.auroraSegments, 2, 1, 12, v -> c.auroraSegments = v);
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
            return;
        }
        if (category == Category.GAMEPLAY) {
            serverToggle("snowWrapping", NaturalityServerConfig.get().snowWrapping, true, v -> NaturalityServerConfig.get().snowWrapping = v);
            serverToggle("snowCompaction", NaturalityServerConfig.get().snowCompaction, true, v -> NaturalityServerConfig.get().snowCompaction = v);
            serverToggle("weatherThaw", NaturalityServerConfig.get().weatherThaw, true, v -> NaturalityServerConfig.get().weatherThaw = v);
            serverToggle("weatherSnowAccumulation", NaturalityServerConfig.get().weatherSnowAccumulation, true, v -> NaturalityServerConfig.get().weatherSnowAccumulation = v);
            addIntro();
            serverToggle("fireWrapping", NaturalityServerConfig.get().fireWrapping, true,
                v -> NaturalityServerConfig.get().fireWrapping = v);
            serverToggle("vanillaPortalEntry", NaturalityServerConfig.get().vanillaPortalEntry, false,
                v -> NaturalityServerConfig.get().vanillaPortalEntry = v);
            return;
        }
        if (category == Category.LIQUIDS) {
            var config = NaturalityConfig.get().liquids;
            var defaults = new NaturalityConfig.Liquids();
            if (query.isBlank()) optionsList().addHeader(Component.translatable("block.minecraft.water"));
            liquidToggle("water", config.water, defaults.water, value -> config.water = value);
            liquidToggle("waterDepth", config.waterDepth, defaults.waterDepth, value -> config.waterDepth = value);
            liquidToggle("waterDistortion", config.waterDistortion, defaults.waterDistortion, value -> config.waterDistortion = value);
            liquidToggle("waterShimmer", config.waterShimmer, defaults.waterShimmer, value -> config.waterShimmer = value);
            liquidToggle("waterParticleTint", config.waterParticleTint, defaults.waterParticleTint, value -> config.waterParticleTint = value);
            liquidToggle("waterRim", config.waterRim, defaults.waterRim, value -> config.waterRim = value);
            liquidToggle("waterWake", config.waterWake, defaults.waterWake, value -> config.waterWake = value);
            liquidToggle("rainRipples", config.rainRipples, defaults.rainRipples, value -> config.rainRipples = value);
            liquidToggle("dripRipples", config.dripRipples, defaults.dripRipples, value -> config.dripRipples = value);
            liquidToggle("waterfallParticles", config.waterfallParticles, defaults.waterfallParticles, value -> config.waterfallParticles = value);
            liquidToggle("waterFallDroplets", config.waterFallDroplets, defaults.waterFallDroplets, value -> config.waterFallDroplets = value);
            liquidToggle("waterImpactColumns", config.waterImpactColumns, defaults.waterImpactColumns, value -> config.waterImpactColumns = value);
            liquidToggle("waterSplashSound", config.waterSplashSound, defaults.waterSplashSound, value -> config.waterSplashSound = value);
            addReset(new OptionInstance<>("naturality.config.liquids.waterDarkDepth", OptionInstance.noTooltip(),
                (caption, value) -> Component.literal(caption.getString() + ": " + value + " blocks"),
                new OptionInstance.IntRange(12, 96), config.waterDarkDepth, value -> config.waterDarkDepth = value), defaults.waterDarkDepth);
            addReset(new OptionInstance<>("naturality.config.liquids.waterPixelSize", OptionInstance.noTooltip(),
                (caption, value) -> Component.literal(caption.getString() + ": " + value),
                new OptionInstance.IntRange(1, 16), config.waterPixelSize, value -> config.waterPixelSize = value), defaults.waterPixelSize);
            if (query.isBlank()) optionsList().addHeader(Component.translatable("block.minecraft.lava"));
            liquidToggle("lava", config.lava, defaults.lava, value -> config.lava = value);
            liquidToggle("flowingLava", config.flowingLava, defaults.flowingLava, value -> config.flowingLava = value);
            liquidToggle("emissiveLava", config.emissiveLava, defaults.emissiveLava, value -> config.emissiveLava = value);
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
            addReset(OptionInstance.createBoolean("naturality.config.fog.enabled",
                OptionInstance.cachedConstantTooltip(Component.translatable("naturality.config.fog.enabled.tooltip")),
                config.enabled, value -> config.enabled = value), defaults.enabled);
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
        var config = NaturalityConfig.get().portalChanges;
        var defaults = new NaturalityConfig.PortalChanges();
        addReset(OptionInstance.createBoolean("naturality.config.blocks", config.portalBlockChanges,
            value -> config.portalBlockChanges = value), defaults.portalBlockChanges);
        addReset(OptionInstance.createBoolean("naturality.config.particles", config.portalParticleChanges,
            value -> config.portalParticleChanges = value), defaults.portalParticleChanges);
        addReset(OptionInstance.createBoolean("naturality.config.glow", config.glowEffect,
            value -> config.glowEffect = value), defaults.glowEffect);
        addReset(volume("openingVolume", config.openingVolume, value -> config.openingVolume = value), defaults.openingVolume);
        addReset(volume("ambientVolume", config.ambientVolume, value -> config.ambientVolume = value), defaults.ambientVolume);
        addReset(volume("travelVolume", config.travelVolume, value -> config.travelVolume = value), defaults.travelVolume);
        toggle("portal.endParallax", config.endParallax, defaults.endParallax, v -> config.endParallax = v);
        toggle("portal.endWalls", config.endWalls, defaults.endWalls, v -> config.endWalls = v);
        toggle("portal.endGlow", config.endGlow, defaults.endGlow, v -> config.endGlow = v);
        toggle("portal.eyeGlow", config.eyeGlow, defaults.eyeGlow, v -> config.eyeGlow = v);
        toggle("portal.suppressEndSmoke", config.suppressEndSmoke, defaults.suppressEndSmoke, v -> config.suppressEndSmoke = v);
    }

    private void serverToggle(String key, boolean value, boolean fallback, java.util.function.Consumer<Boolean> update) {
        String label = "naturality.config.gameplay." + key;
        boolean editable = minecraft.level == null || minecraft.hasSingleplayerServer();
        if (!editable) value = switch (key) {
            case "fireWrapping" -> naturality.config.GameplaySettings.clientFireWrapping();
            case "snowWrapping" -> naturality.config.GameplaySettings.clientSnowWrapping();
            case "snowCompaction" -> naturality.config.GameplaySettings.clientSnowCompaction();
            case "weatherThaw" -> naturality.config.GameplaySettings.clientWeatherThaw();
            case "weatherSnowAccumulation" -> naturality.config.GameplaySettings.clientWeatherSnowAccumulation();
            default -> !naturality.config.GameplaySettings.clientPhysicalPortalEntry();
        };
        var option = OptionInstance.createBoolean(label,
            OptionInstance.cachedConstantTooltip(Component.translatable(label + ".tooltip")), value, update::accept);
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
            OptionInstance.cachedConstantTooltip(Component.translatable(label + ".tooltip")), value, update::accept), fallback);
    }

    private void number(String key, int value, int fallback, int min, int max, java.util.function.IntConsumer update) {
        String label = "naturality.config." + key;
        addReset(new OptionInstance<>(label, OptionInstance.cachedConstantTooltip(Component.translatable(label + ".tooltip")),
            (caption, amount) -> Component.literal(caption.getString() + ": " + amount),
            new OptionInstance.IntRange(min, max), value, update::accept), fallback);
    }

    private void liquidToggle(String key, boolean value, boolean fallback, java.util.function.Consumer<Boolean> update) {
        String label = "naturality.config.liquids." + key;
        addReset(OptionInstance.createBoolean(label,
            OptionInstance.cachedConstantTooltip(Component.translatable(label + ".tooltip")), value, update::accept), fallback);
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
        String label = "naturality.config.clouds." + key;
        addReset(OptionInstance.createBoolean(label, OptionInstance.cachedConstantTooltip(Component.translatable(label + ".tooltip")), value, update::accept), fallback);
    }

    private void cloudNumber(String key, int value, int fallback, int min, int max, java.util.function.IntConsumer update) {
        String label = "naturality.config.clouds." + key;
        addReset(new OptionInstance<>(label, OptionInstance.cachedConstantTooltip(Component.translatable(label + ".tooltip")),
            (caption, amount) -> Component.literal(caption.getString() + ": " + amount + (key.endsWith("Percent") ? "%" : "")),
            new OptionInstance.IntRange(min, max), value, amount -> update.accept(amount)), fallback);
    }

    private void dynamicToggle(String key, boolean value, boolean defaultValue, java.util.function.Consumer<Boolean> update) {
        String label = "naturality.config.dynamicLighting." + key;
        addReset(OptionInstance.createBoolean(label,
            OptionInstance.cachedConstantTooltip(Component.translatable(label + ".tooltip")), value, amount -> update.accept(amount)), defaultValue);
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
        var widget = option.createButton(options, 0, 0, 240);
        if (!query.isBlank()) {
            String haystack = (categoryTitle(buildingCategory).getString() + " " + widget.getMessage().getString())
                .toLowerCase(java.util.Locale.ROOT);
            for (String word : query.strip().toLowerCase(java.util.Locale.ROOT).split("\\s+"))
                if (!haystack.contains(word)) return null;
            if (!searchHeader) { optionsList().addHeader(categoryTitle(buildingCategory)); searchHeader = true; }
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
        if (minecraft.level == null || minecraft.hasSingleplayerServer()) NaturalityServerConfig.get().save();
        if (initialIntro != NaturalityServerConfig.get().portalIntroAnimation
                || !initialGameplay.equals(naturality.config.GameplaySettings.serverSettings())) NaturalityServerConfig.get().save();
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
}


