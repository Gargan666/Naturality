package naturality.test;

import naturality.client.fluid.WaterVisuals;
import naturality.config.NaturalityConfig;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

/** Depth/surface/local-light comparisons in both vanilla transparency paths. */
public final class WaterVisualsGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        var sanitized = new NaturalityConfig.Liquids();
        sanitized.waterDarkDepth = 0;
        sanitized.waterPixelSize = 100;
        sanitized.sanitize();
        if (sanitized.waterDarkDepth != 12 || sanitized.waterPixelSize != 16) throw new AssertionError("Water limits");
        var defaults = new com.google.gson.Gson().fromJson("{}", NaturalityConfig.Liquids.class);
        if (!defaults.waterDepth || !defaults.waterShimmer || !defaults.waterDistortion || defaults.waterDarkDepth != 32)
            throw new AssertionError("Old liquid configs must acquire water defaults");
        var previous = NaturalityConfig.get().liquids;
        boolean[] saved = new boolean[2];
        context.runOnClient(client -> {
            NaturalityConfig.get().liquids = new NaturalityConfig.Liquids();
            saved[0] = client.options.improvedTransparency().get();
            saved[1] = client.gui.hud.isHidden();
            if (!saved[1]) client.gui.hud.toggle();
        });
        try (var world = context.worldBuilder().create()) {
            var server = world.getServer();
            server.runCommand("gamemode spectator @a");
            server.runOnServer(s -> s.setWeatherParameters(12000, 0, false, false));
            server.runCommand("time set 6000");
            server.runCommand("tp @a 0 112 0 0 55");
            server.runOnServer(s -> { for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++) s.overworld().getChunk(x, z); });
            server.runCommand("fill -24 58 -24 24 58 24 sandstone");
            for (int y = 59; y < 103; y += 8)
                server.runCommand("fill -24 " + y + " -24 24 " + Math.min(y + 7, 102) + " 24 water");
            // Raised sand shelf and a deep contrasting checker target.
            server.runCommand("fill -24 96 -24 -8 99 24 smooth_sandstone");
            server.runCommand("fill -4 59 7 4 64 7 white_concrete");
            server.runCommand("fill -1 59 7 1 64 7 black_concrete");
            world.getConnection().waitForClientboundPackets();
            context.waitTicks(45);
            world.getConnection().waitForChunksRender();
            context.runOnClient(client -> {
                float surface = WaterVisuals.surface(0, 0);
                if (Math.abs(surface - 102.875F) > 0.01 || Float.isNaN(surface))
                    throw new AssertionError("Incorrect elevated water surface: " + surface);
            });
            for (boolean oit : new boolean[]{false, true}) {
                context.runOnClient(client -> client.options.improvedTransparency().set(oit));
                server.runCommand("tp @a 0 105 -4 0 35");
                server.runCommand("summon pig 0 102.35 0 {NoAI:1b,NoGravity:1b,Tags:[\"water_rim_test\"]}");
                world.getConnection().waitForClientboundPackets();
                context.waitTicks(10);
                context.runOnClient(client -> {
                    if (naturality.client.fluid.WaterIntersection.lastPixelCount == 0)
                        throw new AssertionError("Posed pig legs must intersect the water surface, OIT="+oit);
                });
                context.takeScreenshot("water-model-rim-"+oit);
                server.runCommand("tp @e[tag=water_rim_test] 0.25 102.65 0.25 35 0");
                server.runCommand("tp @a 2.0 103.8 -1.5 45 45");
                world.getConnection().waitForClientboundPackets();
                context.waitTicks(8);
                context.takeScreenshot("water-model-rim-legs-"+oit);
                context.runOnClient(client -> NaturalityConfig.get().liquids.water=false);
                context.waitTicks(3);
                context.runOnClient(client -> {
                    if(naturality.client.fluid.WaterIntersection.lastPixelCount!=0)
                        throw new AssertionError("Water changes OFF must disable the rim");
                    NaturalityConfig.get().liquids.water=true;
                });
                server.runCommand("tp @e[tag=water_rim_test] 0 100 0");
                world.getConnection().waitForClientboundPackets();
                context.waitTicks(12);
                context.runOnClient(client -> {
                    if (naturality.client.fluid.WaterIntersection.lastPixelCount != 0)
                        throw new AssertionError("Fully submerged models must not leave a rim");
                });
                server.runCommand("tp @e[tag=water_rim_test] 0 105 0");
                world.getConnection().waitForClientboundPackets();
                context.waitTicks(12);
                context.runOnClient(client -> {
                    if (naturality.client.fluid.WaterIntersection.lastPixelCount != 0)
                        throw new AssertionError("Models above water must not leave a rim");
                });
                server.runCommand("kill @e[tag=water_rim_test]");
                server.runCommand("summon oak_boat -16.25 102.6 0.25 {NoGravity:1b,Tags:[\"water_rim_boat\"]}");
                server.runCommand("tp @a -13.5 104 -2.5 45 40");
                world.getConnection().waitForClientboundPackets();
                context.waitTicks(8);
                context.runOnClient(client -> {
                    if(naturality.client.fluid.WaterIntersection.lastPixelCount==0)
                        throw new AssertionError("Boat model must make a water rim across the negative chunk boundary");
                });
                context.takeScreenshot("water-model-rim-boat-"+oit);
                server.runCommand("kill @e[tag=water_rim_boat]");
                String[] views = {"0 113 -9 0 60", "-16 101 -8 0 22", "0 82 0 0 -75", "0 62 0 0 0", "0 62 0 0 -85"};
                String[] names = {"above", "shallows", "mid-surface", "deep-dark", "deep-surface"};
                for (int i = 0; i < views.length; i++) {
                    server.runCommand("tp @a " + views[i]);
                    world.getConnection().waitForClientboundPackets();
                    context.waitTicks(12);
                    context.takeScreenshot("water-" + names[i] + "-" + oit);
                }
                server.runCommand("setblock 0 61 5 sea_lantern");
                server.runCommand("tp @a 0 62 0 0 0");
                world.getConnection().waitForClientboundPackets();
                context.waitTicks(30);
                world.getConnection().waitForChunksRender();
                context.takeScreenshot("water-deep-light-" + oit);
                server.runCommand("setblock 0 61 5 water");
            }
            server.runCommand("tp @a 0 82 0 0 -65");
            world.getConnection().waitForClientboundPackets();
            context.waitTicks(12);
            float[] openDepth = new float[1];
            context.runOnClient(client -> openDepth[0] = WaterVisuals.cameraDepth());
            server.runCommand("fill -3 87 -3 3 87 3 stone");
            world.getConnection().waitForClientboundPackets();
            context.waitTicks(40);
            world.getConnection().waitForChunksRender();
            context.runOnClient(client -> {
                if (Math.abs(WaterVisuals.cameraDepth() - openDepth[0]) > 0.1F)
                    throw new AssertionError("Overhang changed water depth: " + openDepth[0] + " -> " + WaterVisuals.cameraDepth());
            });
            context.takeScreenshot("water-overhang-surface-depth");
            server.runCommand("fill -3 87 -3 3 87 3 water");
            server.runCommand("tp @a -16 101 -8 0 22");
            world.getConnection().waitForClientboundPackets();
            context.waitTicks(12);
            context.runOnClient(client -> NaturalityConfig.get().liquids.waterShimmer = false);
            context.takeScreenshot("water-shimmer-off");
            context.runOnClient(client -> NaturalityConfig.get().liquids.waterShimmer = true);
            context.takeScreenshot("water-shimmer-on");
            server.runCommand("fill -24 100 -24 -8 100 24 smooth_sandstone");
            world.getConnection().waitForClientboundPackets();
            context.waitTicks(40);
            world.getConnection().waitForChunksRender();
            context.takeScreenshot("water-shimmer-less-than-two-blocks");
            server.runCommand("fill -24 100 -24 -8 100 24 water");
            world.getConnection().waitForClientboundPackets();
            context.waitTicks(40);
            context.runOnClient(client -> NaturalityConfig.get().liquids.waterDistortion = false);
            context.takeScreenshot("water-distortion-off");
            context.runOnClient(client -> NaturalityConfig.get().liquids.waterDistortion = true);
            server.runCommand("time set 18000");
            world.getConnection().waitForClientboundPackets();
            context.waitTicks(15);
            context.takeScreenshot("water-shallows-night");
            server.runCommand("time set 6000");
            for (int y = 56; y < 104; y += 8)
                server.runCommand("fillbiome 0 " + y + " -24 24 " + (y + 7) + " 24 minecraft:swamp");
            world.getConnection().waitForClientboundPackets();
            context.waitTicks(40);
            context.runOnClient(client -> {
                float left = WaterVisuals.turbidityAt(-10, 0), middle = WaterVisuals.turbidityAt(0, 0), right = WaterVisuals.turbidityAt(10, 0);
                if (!(left < middle && middle < right && middle > 0.25F && middle < 0.75F))
                    throw new AssertionError("Abrupt biome turbidity: " + left + ", " + middle + ", " + right);
            });
            server.runCommand("tp @a 0 108 -14 0 35");
            world.getConnection().waitForClientboundPackets();
            context.waitTicks(8);
            context.takeScreenshot("water-biome-blend");
            for (String position : new String[]{"-16.0 106 -8.0", "-15.75 106 -8.0", "-15.5 106 -8.0"}) {
                server.runCommand("tp @a " + position + " 0 65");
                world.getConnection().waitForClientboundPackets();
                context.waitTicks(4);
                context.takeScreenshot("water-world-refraction-" + position.split(" ")[0]);
            }
            server.runCommand("tp @a -16 99 -8 0 80");
            world.getConnection().waitForClientboundPackets();
            context.waitTicks(8);
            context.takeScreenshot("water-refraction-near-fade");
            server.runCommand("tp @a -16 101 -8 0 22");
            for (int y = 56; y < 104; y += 8)
                server.runCommand("fillbiome -24 " + y + " -24 24 " + (y + 7) + " 24 minecraft:swamp");
            world.getConnection().waitForClientboundPackets();
            context.waitTicks(40);
            context.takeScreenshot("water-swamp");
            for (String biome : new String[]{"swamp", "mangrove_swamp"}) {
                for (int y = 56; y < 104; y += 8)
                    server.runCommand("fillbiome -24 " + y + " -24 24 " + (y + 7) + " 24 minecraft:" + biome);
                server.runCommand("tp @a 0 88 0 0 -65");
                world.getConnection().waitForClientboundPackets();
                context.waitTicks(40);
                context.runOnClient(client -> {
                    if (WaterVisuals.turbidityAt(0, 0) < 0.99F)
                        throw new AssertionError("Missing swamp turbidity for " + biome);
                });
                context.takeScreenshot("water-surface-fade-" + biome);
            }
            // Floating water with no nearby opaque bed, viewed toward sunset.
            server.runCommand("fill -24 120 -24 24 120 24 water");
            server.runCommand("time set 12000");
            server.runCommand("tp @a 28 121 0 90 8");
            world.getConnection().waitForClientboundPackets();
            context.waitTicks(40);
            world.getConnection().waitForChunksRender();
            context.takeScreenshot("water-bedless-sunset");
            server.runCommand("fill -24 120 -24 24 120 24 air");
            server.runCommand("time set 6000");
            // Covered water beneath another water body must retain immersion
            // fog and must not receive invented skylight from the top column.
            server.runCommand("fill -6 88 -6 6 96 6 stone");
            server.runCommand("fill -5 89 -5 5 94 5 water");
            server.runCommand("tp @a 0 91 0 0 0");
            world.getConnection().waitForClientboundPackets();
            context.waitTicks(40);
            world.getConnection().waitForChunksRender();
            context.takeScreenshot("water-covered-unlit");
            server.runCommand("setblock 0 90 3 sea_lantern");
            world.getConnection().waitForClientboundPackets();
            context.waitTicks(20);
            world.getConnection().waitForChunksRender();
            context.takeScreenshot("water-covered-local-light");
            server.runCommand("gamemode creative @a");
            context.waitTicks(12);
            context.takeScreenshot("water-biome-overlay-first-person");
            server.runCommand("gamemode spectator @a");
            context.runOnClient(client -> {
                var c = NaturalityConfig.get().liquids;
                c.waterDepth = c.waterDistortion = c.waterShimmer = false;
            });
            context.waitTicks(12);
            context.takeScreenshot("water-disabled");
            context.runOnClient(client -> {
                var c = NaturalityConfig.get().liquids;
                c.waterDepth = c.waterDistortion = c.waterShimmer = true;
                client.reloadResourcePacks();
            });
            context.waitTicks(80);
            context.takeScreenshot("water-reloaded");
            // Falling water must not raise the optical surface of its receiving pool.
            server.runCommand("setblock 12 123 0 water");
            server.runCommand("tp @a 12.5 110 -10 0 0");
            world.getConnection().waitForClientboundPackets();
            context.waitTicks(140);
            world.getConnection().waitForChunksRender();
            context.runOnClient(client -> {
                float surface = WaterVisuals.surface(12, 0);
                if (!Float.isFinite(surface) || Math.abs(surface - 102.875F) > 0.01F)
                    throw new AssertionError("Waterfall must retain receiving pool surface: " + surface);
            });
            context.takeScreenshot("water-waterfall-depth");
            server.runCommand("tp @a 12.5 110 0.5 0 0");
            world.getConnection().waitForClientboundPackets();
            context.waitTicks(8);
            context.runOnClient(client -> {
                if (client.gameRenderer.mainCamera().getFluidInCamera() != net.minecraft.world.level.material.FogType.WATER)
                    throw new AssertionError("Waterfall test camera must be immersed");
                if (WaterVisuals.cameraDepth() != 0.0F)
                    throw new AssertionError("Falling water must not give the camera pool depth");
            });
            context.takeScreenshot("water-inside-waterfall");
        } finally {
            context.runOnClient(client -> {
                NaturalityConfig.get().liquids = previous;
                client.options.improvedTransparency().set(saved[0]);
                if (client.gui.hud.isHidden() != saved[1]) client.gui.hud.toggle();
            });
        }
    }
}

