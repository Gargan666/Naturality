package naturality.test;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.renderer.state.LightmapRenderState;
import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import naturality.client.lighting.HardcoreDarkness;

public final class HardcoreDarknessGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        checkConfig();
        context.runOnClient(client -> {
            client.gui.setScreen(new naturality.client.config.NaturalityConfigScreen(null));
            press(client.gui.screen(), "Lighting and Atmosphere");
            press(client.gui.screen(), "Hardcore Darkness");
        });
        context.takeScreenshot("hardcore-settings-categories");
        for (String page : new String[] {"Cave Darkness", "Moon Phase Darkness"}) {
            context.runOnClient(client -> press(client.gui.screen(), page));
            context.takeScreenshot("hardcore-settings-" + page.replace(' ', '-'));
            context.runOnClient(client -> client.gui.screen().onClose());
        }
        context.runOnClient(client -> {
            client.gui.screen().onClose();
            client.gui.screen().onClose();
            client.gui.screen().onClose();
        });
        try (var world = context.worldBuilder().create()) {
            var server = world.getServer();
            server.runCommand("gamerule minecraft:advance_time false");
            server.runOnServer(s -> s.setWeatherParameters(12000, 0, false, false));
            server.runCommand("gamemode spectator @a");
            server.runCommand("fill -8 100 -8 8 100 8 smooth_quartz");
            server.runCommand("tp @a 0 102 0 0 -25");
            for (int phase = 0; phase <= 4; phase++) {
                server.runCommand("time set " + (phase * 24000 + 18000));
                world.getConnection().waitForClientboundPackets();
                context.waitTicks(10);
                final float expected = (4 - phase) / 4.0F;
                context.runOnClient(client -> {
                    float actual = HardcoreDarkness.atmosphere(client.level, client.gameRenderer.mainCamera(), 0);
                    if (Math.abs(actual - expected) > 0.001F) throw new AssertionError("Moon factor " + actual + " != " + expected);
                    var extractor = new LightmapRenderStateExtractor(client.gameRenderer, client);
                    extractor.tick();
                    var state = new LightmapRenderState();
                    extractor.extract(state, 0);
                    if (state.ambientColor.lengthSquared() != 0) throw new AssertionError("Ambient light remains");
                    if (expected == 0 && state.skyFactor != 0) throw new AssertionError("New moon skylight remains");
                });
                context.takeScreenshot("hardcore-moon-" + phase);
            }
            context.runOnClient(client -> checkControls(client));
            server.runCommand("time set noon");
            server.runCommand("fill -5 101 -5 5 108 5 stone hollow");
            server.runCommand("tp @a 0 103 0 0 10");
            world.getConnection().waitForClientboundPackets();
            context.waitTicks(30);
            world.getConnection().waitForChunksRender();
            context.takeScreenshot("hardcore-cave-black");
            server.runCommand("setblock 0 101 2 torch");
            world.getConnection().waitForClientboundPackets();
            context.waitTicks(30);
            world.getConnection().waitForChunksRender();
            context.takeScreenshot("hardcore-cave-torch");
            server.runCommand("time set 114000");
            for (var dimension : new net.minecraft.resources.ResourceKey[] {
                    net.minecraft.world.level.Level.NETHER, net.minecraft.world.level.Level.END}) {
                server.runCommand("execute in " + dimension.identifier() + " run tp @a 0 150 0 0 -20");
                world.getConnection().waitForClientboundPackets();
                context.waitFor(client -> client.level != null && client.level.dimension().equals(dimension));
                context.waitTicks(10);
                var original = naturality.config.NaturalityConfig.get().hardcoreDarkness;
                try {
                    context.runOnClient(client -> {
                        var config = naturality.config.NaturalityConfig.get();
                        config.hardcoreDarkness = new naturality.config.NaturalityConfig.HardcoreDarkness();
                        var camera = client.gameRenderer.mainCamera();
                        var extractor = new LightmapRenderStateExtractor(client.gameRenderer, client);
                        var state = new LightmapRenderState();
                        extractor.tick();
                        extractor.extract(state, 0);
                        var vanillaAmbient = camera.attributeProbe().getValue(net.minecraft.world.attribute.EnvironmentAttributes.AMBIENT_LIGHT_COLOR, 0);
                        float vanillaSky = camera.attributeProbe().getValue(net.minecraft.world.attribute.EnvironmentAttributes.SKY_LIGHT_FACTOR, 0);
                        if (!state.ambientColor.equals(vanillaAmbient) || state.skyFactor != vanillaSky
                                || HardcoreDarkness.sky(client.level, camera, 0) != 1)
                            throw new AssertionError("Excluded dimension changed: " + dimension);
                        config.hardcoreDarkness.caves.dimensions.nether = true;
                        config.hardcoreDarkness.caves.dimensions.end = true;
                        config.hardcoreDarkness.moon.dimensions.nether = true;
                        config.hardcoreDarkness.moon.dimensions.end = true;
                        extractor.tick();
                        extractor.extract(state, 0);
                        if (state.ambientColor.lengthSquared() != 0 || state.skyFactor != 0
                                || HardcoreDarkness.sky(client.level, camera, 0) != 0)
                            throw new AssertionError("Dimension opt-in not applied: " + dimension);
                    });
                    context.waitTicks(5);
                    context.takeScreenshot("hardcore-opt-in-" + dimension.identifier().getPath());
                } finally {
                    context.runOnClient(client -> naturality.config.NaturalityConfig.get().hardcoreDarkness = original);
                }
            }
        }
    }

    private static void press(net.minecraft.client.gui.screens.Screen screen, String label) {
        if (!findAndPress(screen, label)) throw new AssertionError("Missing category: " + label);
    }

    private static boolean findAndPress(net.minecraft.client.gui.components.events.GuiEventListener node, String label) {
        if (node instanceof net.minecraft.client.gui.components.Button button
                && button.getMessage().getString().equals(label)) {
            button.onPress(null);
            return true;
        }
        if (node instanceof net.minecraft.client.gui.components.events.ContainerEventHandler container)
            for (var child : container.children()) if (findAndPress(child, label)) return true;
        return false;
    }

    private static void checkConfig() {
        var gson = new com.google.gson.Gson();
        var defaults = gson.fromJson("{}", naturality.config.NaturalityConfig.class);
        if (!defaults.hardcoreDarkness.caves.enabled || !defaults.hardcoreDarkness.moon.enabled)
            throw new AssertionError("Old configs must retain darkness defaults");
        var settings = defaults.hardcoreDarkness;
        for (var dimensions : new naturality.config.NaturalityConfig.DarknessDimensions[] {settings.caves.dimensions, settings.moon.dimensions}) {
            if (!dimensions.includes(net.minecraft.world.level.Level.OVERWORLD)
                    || dimensions.includes(net.minecraft.world.level.Level.NETHER)
                    || dimensions.includes(net.minecraft.world.level.Level.END))
                throw new AssertionError("Darkness must default to Overworld only");
            var custom = net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
                net.minecraft.resources.Identifier.fromNamespaceAndPath("naturality", "test_dimension"));
            if (dimensions.includes(custom)) throw new AssertionError("Custom dimensions must be opt-in");
            dimensions.nether = true;
            dimensions.end = true;
            dimensions.other = true;
            if (!dimensions.includes(net.minecraft.world.level.Level.NETHER)
                    || !dimensions.includes(net.minecraft.world.level.Level.END) || !dimensions.includes(custom))
                throw new AssertionError("Dimension opt-in failed");
        }
        settings.caves.ambientPercent = -20;
        settings.moon.newPercent = 120;
        settings.sanitize();
        if (settings.caves.ambientPercent != 0 || settings.moon.newPercent != 100)
            throw new AssertionError("Invalid percentages not clamped");
        settings.caves.enabled = false;
        settings.moon.darkenClouds = false;
        var restored = gson.fromJson(gson.toJson(settings), naturality.config.NaturalityConfig.HardcoreDarkness.class);
        if (restored.caves.enabled || restored.moon.darkenClouds || restored.moon.newPercent != 100)
            throw new AssertionError("Darkness settings did not round-trip");
        restored.caves = null;
        restored.moon = null;
        restored.sanitize();
        if (restored.caves == null || restored.moon == null) throw new AssertionError("Missing child settings not restored");
    }

    private static void checkControls(net.minecraft.client.Minecraft client) {
        var config = naturality.config.NaturalityConfig.get();
        var original = config.hardcoreDarkness;
        config.hardcoreDarkness = new naturality.config.NaturalityConfig.HardcoreDarkness();
        try {
            var settings = config.hardcoreDarkness;
            var camera = client.gameRenderer.mainCamera();
            settings.caves.enabled = false;
            var vanillaAmbient = camera.attributeProbe().getValue(net.minecraft.world.attribute.EnvironmentAttributes.AMBIENT_LIGHT_COLOR, 0);
            var extractor = new LightmapRenderStateExtractor(client.gameRenderer, client);
            var state = new LightmapRenderState();
            extractor.tick();
            extractor.extract(state, 0);
            if (!state.ambientColor.equals(vanillaAmbient) || state.skyFactor != 0)
                throw new AssertionError("Cave toggle must restore ambient without disabling new moon darkness");
            settings.caves.enabled = true;
            settings.caves.ambientPercent = 50;
            extractor.tick();
            extractor.extract(state, 0);
            if (new org.joml.Vector3f(vanillaAmbient).mul(0.5F).distance(state.ambientColor) > 0.00001F)
                throw new AssertionError("Ambient percentage not applied");
            settings.moon.newPercent = 35;
            if (Math.abs(HardcoreDarkness.atmosphere(client.level, camera, 0) - 0.35F) > 0.001F)
                throw new AssertionError("Custom phase brightness not applied");
            settings.moon.darkenSky = false;
            settings.moon.darkenClouds = false;
            if (HardcoreDarkness.sky(client.level, camera, 0) != 1 || HardcoreDarkness.clouds(client.level, camera, 0) != 1)
                throw new AssertionError("Sky/cloud toggles not applied");
            settings.moon.enabled = false;
            extractor.tick();
            extractor.extract(state, 0);
            float vanillaSky = camera.attributeProbe().getValue(net.minecraft.world.attribute.EnvironmentAttributes.SKY_LIGHT_FACTOR, 0);
            if (state.skyFactor != vanillaSky || HardcoreDarkness.ambientMultiplier(client.level) != 0.5F)
                throw new AssertionError("Moon toggle must restore skylight without disabling cave darkness");
            settings.caves.dimensions.overworld = false;
            settings.moon.enabled = true;
            settings.moon.dimensions.overworld = false;
            extractor.tick();
            extractor.extract(state, 0);
            if (!state.ambientColor.equals(vanillaAmbient) || state.skyFactor != vanillaSky)
                throw new AssertionError("Dimension exclusions must restore normal lighting");
        } finally {
            config.hardcoreDarkness = original;
        }
    }
}
