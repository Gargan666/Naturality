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
        suites.put("environment-persistence", new EnvironmentPersistenceGameTest());
        suites.put("entity-shadow", new EntityShadowGameTest());
        suites.put("crowd-panic", new CrowdPanicGameTest());
        suites.put("villagers", new VillagerWorkGameTest());
        suites.put("butcher", new ButcherWorkGameTest());
        suites.put("villager-bread", new VillagerBreadGameTest());
        suites.put("breaking", new BreakingTexturesGameTest());
        suites.put("chunks", new ChunkSafetyGameTest());
        suites.put("snow-compat", new SnowRendererCompatibilityGameTest());
        suites.put("wind-compat", new WindRendererGameTest());
        suites.put("wind-shapes", new WindShapesGameTest());
        suites.put("sky-events", new SkyEventsGameTest());
        suites.put("sunset", new SunsetGameTest());
        suites.put("aurora", new AuroraGameTest());
        suites.put("rainbow", new RainbowGameTest());
        suites.put("config", new ConfigMenuGameTest());
        suites.put("glint", new GlintContourGameTest());
        suites.put("rain-splash", new RainSplashGameTest());
        suites.put("weather", new WeatherGameTest());
        suites.put("particle-weather", new ParticleWeatherGameTest());
        suites.put("particle-visibility", new ParticleVisibilityGameTest());
        suites.put("weather-commands", new WeatherCommandsGameTest());
        suites.put("gameplay", new GameplaySettingsGameTest());
        suites.put("fluids", new FluidsGameTest());
        suites.put("fluid-tail", new FluidTailGameTest());
        suites.put("water", new WaterVisualsGameTest());
        suites.put("water-rain", new WaterRainFogGameTest());
        suites.put("waterfall", new WaterfallGameTest());
        suites.put("lava-effects", new LavaEffectsGameTest());
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




