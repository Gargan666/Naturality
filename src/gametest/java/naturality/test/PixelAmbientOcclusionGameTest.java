package naturality.test;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

/** Small isolated scene for shader compilation and block-grid visual regression checks. */
public final class PixelAmbientOcclusionGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        context.runOnClient(client -> client.gui.setScreen(new naturality.client.config.NaturalityConfigScreen(client.gui.screen())));
        context.waitTicks(2);
        context.takeScreenshot("lighting-settings");
        for (var category : naturality.client.config.NaturalityCategoryScreen.Category.values()) {
            context.runOnClient(client -> {
                if (category == naturality.client.config.NaturalityCategoryScreen.Category.LIGHTING) {
                    naturality.config.NaturalityConfig.get().lighting.darkStepPercent = 3;
                } else if (category == naturality.client.config.NaturalityCategoryScreen.Category.FOG) {
                    naturality.config.NaturalityConfig.get().fog.pixelSize = 12;
                } else {
                    naturality.config.NaturalityConfig.get().portalChanges.ambientVolume = 0.3;
                }
            });
            context.runOnClient(client -> client.gui.setScreen(
                new naturality.client.config.NaturalityCategoryScreen(client.gui.screen(), category)));
            context.runOnClient(client -> {
                int count = pressResets(client.gui.screen());
                int expected = switch (category) { case LIGHTING -> 7; case DYNAMIC_LIGHTING -> 10 + naturality.config.NaturalityConfig.DynamicLighting.defaultEntityLights().size(); case CAVE_DARKNESS -> 7; case MOON_DARKNESS -> 12; case CLOUDS -> 23; case LIQUIDS -> 25; case FOG -> 5; case PORTAL -> 11; case SKY -> 3; case FIRE -> 2; case PARTICLES -> 7; case GAMEPLAY -> 7; case WEATHER, GLINT -> 5; case SNOW -> 1; default -> 0; };
                if (count != expected) throw new AssertionError("Reset buttons for " + category + ": expected " + expected + ", got " + count);
                var config = naturality.config.NaturalityConfig.get();
                if (category == naturality.client.config.NaturalityCategoryScreen.Category.FOG
                        && config.fog.pixelSize != new naturality.config.NaturalityConfig.Fog().pixelSize)
                    throw new AssertionError("Fog reset did not apply");
                if (category == naturality.client.config.NaturalityCategoryScreen.Category.LIGHTING
                        && config.lighting.darkStepPercent != new naturality.config.NaturalityConfig.Lighting().darkStepPercent)
                    throw new AssertionError("Lighting reset did not apply");
                if (category == naturality.client.config.NaturalityCategoryScreen.Category.PORTAL
                        && config.portalChanges.ambientVolume != new naturality.config.NaturalityConfig.PortalChanges().ambientVolume)
                    throw new AssertionError("Volume reset did not apply");
            });
            context.waitTicks(2);
            context.takeScreenshot("settings-" + category.name().toLowerCase(java.util.Locale.ROOT));
            context.runOnClient(client -> client.gui.screen().onClose());
            context.waitFor(client -> client.gui.overlay() == null);
            context.runOnClient(client -> {
                var config = naturality.config.NaturalityConfig.get();
                if (category == naturality.client.config.NaturalityCategoryScreen.Category.LIGHTING
                        && config.lighting.darkStepPercent != new naturality.config.NaturalityConfig.Lighting().darkStepPercent)
                    throw new AssertionError("Closing the page overwrote the reset slider value");
            });
        }
        context.runOnClient(client -> client.gui.screen().onClose());
        try (var world = context.worldBuilder().create()) {
            var server = world.getServer();
            server.runCommand("time set noon");
            server.runOnServer(s -> s.setWeatherParameters(12000, 0, false, false));
            server.runCommand("gamemode spectator @a");
            server.runCommand("fill -8 100 -8 8 108 8 air");
            server.runCommand("fill -5 100 -5 5 100 5 smooth_quartz");
            server.runCommand("fill -5 101 3 5 104 3 smooth_quartz");
            server.runCommand("fill 2 101 -3 2 104 3 smooth_quartz");
            server.runCommand("setblock 0 101 0 stone");
            server.runCommand("setblock -2 101 1 stone_slab");
            server.runCommand("setblock -3 101 1 stone_stairs[facing=south]");
            server.runCommand("setblock -1 101 2 oak_leaves[persistent=true]");
            server.runCommand("setblock 1 101 2 glass");
            server.runCommand("tp @a -1.5 102 -3 0 25");
            world.getConnection().waitForClientboundPackets();
            world.getConnection().waitForChunksRender();
            context.waitTicks(10);
            context.takeScreenshot("pixel-ao-front");
            server.runCommand("tp @a -3 102 -1 -45 30");
            world.getConnection().waitForClientboundPackets();
            context.waitTicks(10);
            context.takeScreenshot("pixel-ao-oblique");
            // A roof edge creates a skylight gradient; an enclosed torch scene
            // exercises block light independently of daytime sky illumination.
            server.runCommand("fill -5 105 0 2 105 3 smooth_quartz");
            world.getConnection().waitForClientboundPackets();
            context.waitTicks(20);
            world.getConnection().waitForChunksRender();
            context.takeScreenshot("pixel-light-skylight");
            server.runCommand("time set midnight");
            server.runCommand("fill -5 105 -5 5 105 3 smooth_quartz");
            server.runCommand("setblock 1 101 1 torch");
            world.getConnection().waitForClientboundPackets();
            context.waitTicks(20);
            world.getConnection().waitForChunksRender();
            context.takeScreenshot("pixel-light-torch");
            server.runCommand("setblock 1 101 1 air");
            world.getConnection().waitForClientboundPackets();
            context.waitTicks(20);
            world.getConnection().waitForChunksRender();
            context.takeScreenshot("pixel-light-dark");
            // Exercise the equal-endpoint path (no logarithmic division by zero)
            // and resource reload using non-default settings, then restore them.
            var config = naturality.config.NaturalityConfig.get();
            var original = config.lighting;
            try {
                config.lighting = new naturality.config.NaturalityConfig.Lighting();
                config.lighting.darkStepPercent = 2;
                config.lighting.lightStepPercent = 2;
                config.lighting.darkSaturationPercent = 60;
                reloadLighting(context);
                context.takeScreenshot("pixel-light-custom-settings");
            } finally {
                config.lighting = original;
                reloadLighting(context);
            }
        }
    }

    private static void reloadLighting(ClientGameTestContext context) {
        var future = new java.util.concurrent.atomic.AtomicReference<java.util.concurrent.CompletableFuture<Void>>();
        context.runOnClient(client -> future.set(client.reloadResourcePacks()));
        context.waitFor(client -> future.get().isDone());
        future.get().join();
        context.waitTicks(5);
    }

    private static int pressResets(net.minecraft.client.gui.components.events.GuiEventListener element) {
        if (element instanceof net.minecraft.client.gui.components.Button button
                && button.getMessage().getString().equals("Reset")) {
            button.onPress(null);
            return 1;
        }
        int count = 0;
        if (element instanceof net.minecraft.client.gui.components.events.ContainerEventHandler container) {
            for (var child : container.children()) count += pressResets(child);
        }
        return count;
    }
}

