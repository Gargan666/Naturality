package naturality.test;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Arrays;

/** Run only explicitly selected suites; preserve order and reject typos. */
public final class SelectedClientGameTests implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        var suites = new LinkedHashMap<String, FabricClientGameTest>();
        suites.put("creeper-biome", new CreeperBiomeGameTest());
        suites.put("maddened-creeper", new MaddenedCreeperGameTest());
        suites.put("maddened-zombie", new MaddenedZombieGameTest());
        suites.put("maddened-spawns", new MaddenedSpawnsGameTest());
        suites.put("insanity", new InsanityGameTest());
        suites.put("ambient-horror", new AmbientHorrorGameTest());
        suites.put("client-startup", new ClientStartupGameTest());
        suites.put("menu-world", new MenuWorldGameTest());
        suites.put("hardcore-lighting", new NativeHardcoreLightingGameTest());
        suites.put("shader-stall", new ShaderStallGameTest());
        suites.put("sleep", new SleepAccelerationGameTest());
        suites.put("render-detail", new RenderDetailGameTest());
        suites.put("terrain-lod-world", new TerrainLodWorldGameTest());
        suites.put("feature-startup", new FeatureStartupGameTest());
        suites.put("end-terrain", new EndTerrainGameTest());
        suites.put("end-chunk-performance", new EndChunkPerformanceGameTest());
        suites.put("end-save", new EndSaveGameTest());
        suites.put("smooth-endstone", new SmoothEndstoneGameTest());
        suites.put("lirestone-worldgen", new LirestoneWorldgenGameTest());
        suites.put("lirestone-crags", new LirestoneCragsGameTest());
        suites.put("end-wilds", new EndWildsGameTest());
        suites.put("chorus-flowers", new ChorusFlowersGameTest());
        suites.put("chorus-sprouts", new ChorusSproutsGameTest());
        suites.put("fireball-model", new FireballModelGameTest());
        suites.put("confusion", new ConfusionGameTest());
        suites.put("crystal-visual", new CrystalVisualGameTest());
        suites.put("fish-swim", new FishSwimGameTest());
        suites.put("zombie-walk", new ZombieWalkGameTest());
        suites.put("floating-mob-tilt", new FloatingMobTiltGameTest());
        suites.put("ghast-squash", new GhastSquashGameTest());
        suites.put("ghast-crash", new GhastCrashGameTest());
        suites.put("mob-arrows", new MobArrowsGameTest());
        suites.put("epilepsy-mode", new EpilepsyModeGameTest());
        suites.put("lightning", new LightningGameTest());
        suites.put("distortion-interactions", new DistortionInteractionGameTest());
        suites.put("distortion-creative", new DistortionCreativeFlightGameTest());
        suites.put("distortion", new DistortionGameTest());
        suites.put("rise", new RiseGameTest());
        suites.put("starfall", new StarfallGameTest());
        suites.put("end-weather", new EndWeatherGameTest());
        suites.put("end-flight", new EndFlightGameTest());
        suites.put("end-movement", new EndMovementGameTest());
        suites.put("end-mob-ai", new EndMobAiGameTest());
        suites.put("sky-state", new SkyStateGameTest());
        suites.put("snow-worldgen", new SnowWorldgenGameTest());
        suites.put("mountain-waterfalls", new MountainWaterfallGameTest());
        suites.put("environment-persistence", new EnvironmentPersistenceGameTest());
        suites.put("entity-shadow", new EntityShadowGameTest());
        suites.put("crowd-panic", new CrowdPanicGameTest());
        suites.put("villagers", new VillagerWorkGameTest());
        suites.put("butcher", new ButcherWorkGameTest());
        suites.put("villager-bread", new VillagerBreadGameTest());
        suites.put("villager-gates", new VillagerGateGameTest());
        suites.put("villager-shelter", new VillagerShelterGameTest());
        suites.put("animal-care", new AnimalCareGameTest());
        suites.put("reputation", new ReputationGameTest());
        suites.put("navigation", new NavigationGameTest());
        suites.put("camera-hands", new CameraHandsGameTest());
        suites.put("breaking", new BreakingTexturesGameTest());
        suites.put("chunks", new ChunkSafetyGameTest());
        suites.put("snow-weather", new SnowAccumulationGameTest());
        suites.put("snow-storm", new SnowStormGameTest());
        suites.put("snow-performance", new SnowPerformanceGameTest());
        suites.put("snow-compat", new SnowRendererCompatibilityGameTest());
        suites.put("wind-compat", new WindRendererGameTest());
        suites.put("wind-shapes", new WindShapesGameTest());
        suites.put("sky-events", new SkyEventsGameTest());
        suites.put("proximity", new ProximityGameTest());
        suites.put("blood-moon", new BloodMoonGameTest());
        suites.put("end-flashes", new EndFlashesGameTest());
        suites.put("cave-ambience", new CaveAmbienceGameTest());
        suites.put("sunset", new SunsetGameTest());
        suites.put("aurora", new AuroraGameTest());
        suites.put("rainbow", new RainbowGameTest());
        suites.put("config", new ConfigMenuGameTest());
        suites.put("cloud-geometry", c -> c.runOnClient(_ -> CloudsGameTest.checkGeometry()));
        suites.put("darkness-config", _ -> HardcoreDarknessGameTest.checkConfig());
        suites.put("portal-metadata", c -> c.runOnClient(_ -> ForestPerformanceGameTest.checkPortalMetadata()));
        suites.put("glint", new GlintContourGameTest());
        suites.put("rain-splash", new RainSplashGameTest());
        suites.put("weather", new WeatherGameTest());
        suites.put("particle-weather", new ParticleWeatherGameTest());
        suites.put("particle-visibility", new ParticleVisibilityGameTest());
        suites.put("weather-commands", new WeatherCommandsGameTest());
        suites.put("gameplay", new GameplaySettingsGameTest());
        suites.put("fluids", new FluidsGameTest());
        suites.put("fluid-tail", new FluidTailGameTest());
        suites.put("liquid-projectiles", new LiquidProjectileSplashGameTest());
        suites.put("liquid-slopes", new LiquidSlopesGameTest());
        suites.put("water", new WaterVisualsGameTest());
        suites.put("fog-culling", new FogCullingGameTest());
        suites.put("terrain-culling", new TerrainCullingGameTest());
        suites.put("water-rain", new WaterRainFogGameTest());
        suites.put("waterfall", new WaterfallGameTest());
        suites.put("lava-effects", new LavaEffectsGameTest());
        suites.put("heat-waves", new HeatWavesGameTest());
        suites.put("bell-lighting", new BellLightingGameTest());
        suites.put("snow-surface-lighting", new SnowSurfaceLightingGameTest());
        suites.put("overlay-edge", new OverlayEdgeGameTest());
        suites.put("water-ripples", new WaterRipplesGameTest());
        suites.put("water-occlusion", new WaterFogOcclusionGameTest());
        suites.put("short-distance", new ShortDistanceGameTest());
        suites.put("fog", context1 -> {
            new PixelFogGameTest().runTest(context1);
            new OverworldFogGameTest().runTest(context1);
        });
        suites.put("clouds", new CloudsGameTest());
        suites.put("smoke", new SmokeGameTest());
        suites.put("flame", new FlameGameTest());
        suites.put("fire", new SurfaceFireGameTest());
        suites.put("snow", new SnowloggingGameTest());
        suites.put("leaves", new LeavesGameTest());
        suites.put("lighting", context1 -> {
            new PixelAmbientOcclusionGameTest().runTest(context1);
            new HardcoreDarknessGameTest().runTest(context1);
        });
        suites.put("render-redundancy", new RenderRedundancyGameTest());
        suites.put("forest-performance", new ForestPerformanceGameTest());
        suites.put("overworld-loading", new OverworldLoadingGameTest());
        suites.put("water-light-performance", new WaterLightPerformanceGameTest());
        suites.put("dynamic-lighting", new DynamicLightingGameTest());
        suites.put("portal", new PortalOpeningGameTest());
        suites.put("end-portal", context1 -> {
            new EndPortalParallaxGameTest().runTest(context1);
            new EndEyeGlowGameTest().runTest(context1);
        });
        suites.put("audio", new TestAudioIsolationGameTest());
        var selected = new LinkedHashSet<>(Arrays.asList(
            System.getProperty("naturality.test.suites", "").split(",", -1)));
        var names = new LinkedHashSet<String>();
        for (String value : selected) {
            String name = value.trim();
            if (name.equals("all")) names.addAll(suites.keySet());
            else if (suites.containsKey(name)) names.add(name);
            else throw new IllegalArgumentException("Unknown client test suite: " + name);
        }
        for (String name : names) {
            System.out.println("Naturality client test suite: " + name);
            suites.get(name).runTest(context);
        }
    }
}


