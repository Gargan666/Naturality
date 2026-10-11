package naturality.test;

import naturality.config.NaturalityConfig;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.CloudStatus;

/** Exercises mesh rebuilds and every cloud pipeline in an isolated client world. */
public final class CloudsGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        var saved = NaturalityConfig.get().clouds;
        boolean savedFog = NaturalityConfig.get().fog.enabled;
        var oldClouds = new CloudStatus[1];
        boolean[] old = new boolean[2];
        context.runOnClient(client -> {
            checkGeometry();
            oldClouds[0] = client.options.cloudStatus().get();
            old[0] = client.gui.hud.isHidden();
            old[1] = client.options.improvedTransparency().get();
            client.options.cloudStatus().set(CloudStatus.FANCY);
            if (!old[0]) client.gui.hud.toggle();
            NaturalityConfig.get().clouds = new NaturalityConfig.Clouds();
            NaturalityConfig.get().clouds.base.speedPercent = 0;
            NaturalityConfig.get().clouds.upper.speedPercent = 0;
        });
        try (var world = context.worldBuilder().create()) {
            var server = world.getServer();
            server.runCommand("gamemode spectator @a");
            server.runOnServer(s -> s.setWeatherParameters(12000, 0, false, false));
            server.runCommand("time set 6000");
            var edge = new double[2];
            context.runOnClient(client -> {
                try (var input = client.getResourceManager().open(net.minecraft.resources.Identifier.withDefaultNamespace("textures/environment/clouds.png"));
                     var texture = com.mojang.blaze3d.platform.NativeImage.read(input)) {
                    outer: for (int z = -8; z <= 8; z++) for (int x = -8; x <= 8; x++) {
                        int tx = Math.floorMod(x, texture.getWidth());
                        int tz = Math.floorMod(z, texture.getHeight());
                        if (net.minecraft.util.ARGB.alpha(texture.getPixel(tx, tz)) >= 10
                            && net.minecraft.util.ARGB.alpha(texture.getPixel(tx, Math.floorMod(tz - 1, texture.getHeight()))) < 10) {
                            edge[0] = x * 12 + 6;
                            edge[1] = z * 12 - 3.96 - 5;
                            break outer;
                        }
                    }
                } catch (java.io.IOException exception) { throw new AssertionError(exception); }
            });
            for (boolean oit : new boolean[]{false, true}) {
                context.runOnClient(client -> client.options.improvedTransparency().set(oit));
                server.runCommand("tp @a " + edge[0] + " 192 " + edge[1] + " 0 0");
                world.getConnection().waitForClientboundPackets();
                context.waitTicks(8);
                context.takeScreenshot("clouds-pixels-" + oit);
                context.runOnClient(client -> {
                    var origin = new net.minecraft.world.phys.Vec3(edge[0], 180, edge[1] + 8);
                    float transmission = naturality.client.cloud.CloudSunOcclusion.transmission(origin, origin.add(0, 200, 0));
                    if (transmission >= 0.7F)
                        throw new AssertionError("Rendered cloud occupancy must reach the sun-flare overlay: " + transmission);
                });
                boolean fogEnabled = NaturalityConfig.get().fog.enabled;
                context.runOnClient(client -> {
                    var c = NaturalityConfig.get().clouds;
                    c.base.thickness = 16;
                    c.upper.thickness = 16;
                    c.upper.heightOffset = 2;
                    c.upper.offsetX = 0;
                    c.upper.offsetZ = 4;
                });
                server.runCommand("tp @a " + edge[0] + " 200 " + edge[1] + " 0 0");
                world.getConnection().waitForClientboundPackets();
                context.waitTicks(8);
                context.takeScreenshot("clouds-overlap-occluded-" + oit);
                context.runOnClient(client -> NaturalityConfig.get().fog.enabled = false);
                context.waitTicks(8);
                context.takeScreenshot("clouds-overlap-no-fog-" + oit);
                context.runOnClient(client -> {
                    NaturalityConfig.get().fog.enabled = fogEnabled;
                    var c = NaturalityConfig.get().clouds;
                    c.base.thickness = 4;
                    c.upper = NaturalityConfig.Clouds.upperDefaults();
                    c.upper.speedPercent = 0;
                });
                server.runCommand("tp @a " + edge[0] + " 192 " + edge[1] + " 0 0");
                world.getConnection().waitForClientboundPackets();
                // A distant opaque backdrop must receive edge fog before the nearby cloud.
                int wallX = (int) edge[0];
                int wallZ = (int) Math.floor(edge[1]) + 76;
                server.runOnServer(s -> {
                    for (int x = (wallX - 24) >> 4; x <= (wallX + 24) >> 4; x++)
                        s.overworld().getChunk(x, wallZ >> 4);
                });
                server.runCommand("fill " + (wallX - 24) + " 178 " + wallZ + " " + (wallX + 24) + " 210 " + wallZ + " stone");
                server.runOnServer(s -> {
                    if (!s.overworld().getBlockState(new net.minecraft.core.BlockPos(wallX, 192, wallZ))
                            .is(net.minecraft.world.level.block.Blocks.STONE)) throw new AssertionError("Cloud fog backdrop missing");
                });
                world.getConnection().waitForClientboundPackets();
                world.getConnection().waitForChunksRender();
                context.waitTicks(8);
                context.takeScreenshot("clouds-before-edge-fog-" + oit);
                server.runCommand("time set 18000");
                context.waitTicks(8);
                context.takeScreenshot("clouds-before-edge-fog-night-" + oit);
                server.runCommand("time set 6000");
                server.runCommand("fill " + (wallX - 24) + " 178 " + wallZ + " " + (wallX + 24) + " 210 " + wallZ + " air");
                world.getConnection().waitForClientboundPackets();
                world.getConnection().waitForChunksRender();
                server.runCommand("tp @a " + edge[0] + " 192 " + (edge[1] + 10) + " 180 0");
                world.getConnection().waitForClientboundPackets();
                context.waitTicks(8);
                context.takeScreenshot("clouds-inward-hidden-" + oit);
                server.runCommand("tp @a " + edge[0] + " 197 " + (edge[1] + 10) + " 180 65");
                world.getConnection().waitForClientboundPackets();
                context.waitTicks(8);
                context.takeScreenshot("clouds-bottom-from-above-" + oit);
                server.runCommand("tp @a " + edge[0] + " 192 " + edge[1] + " 0 0");
                world.getConnection().waitForClientboundPackets();
                if (oit) {
                    server.runCommand("time set 18000");
                    context.waitTicks(8);
                    context.takeScreenshot("clouds-pixels-night");
                    server.runCommand("time set 6000");
                }
                for (int height : new int[]{185, 190, 191, 192, 193, 194, 195, 197, 215, 239}) {
                    server.runCommand("tp @a 0.5 " + height + " 0.5 20 " + (height < 210 ? -12 : 15));
                    world.getConnection().waitForClientboundPackets();
                    context.waitTicks(8);
                    context.takeScreenshot("clouds-" + (oit ? "oit-" : "classic-") + height);
                }
                context.runOnClient(client -> {
                    var c = NaturalityConfig.get().clouds;
                    c.base.style = 2;
                    c.upper.style = 1;
                    c.upper.fadingSides = false;
                    c.upper.width = 24;
                    c.upper.thickness = 12;
                });
                context.waitTicks(8);
                context.takeScreenshot("clouds-mixed-" + oit);
                context.runOnClient(client -> {
                    var c = NaturalityConfig.get().clouds;
                    c.base.style = 0;
                    c.upper = NaturalityConfig.Clouds.upperDefaults();
                    c.upper.speedPercent = 0;
                });
            }
            // Look at the moon through a known occupied texture cell: full-opacity
            // caps must block the sky, while reduced opacity deliberately reveals it.
            server.runCommand("tp @a " + edge[0] + " 176 " + (edge[1] + 11) + " 0 -90");
            world.getConnection().waitForClientboundPackets();
            java.nio.file.Path opaqueMoon = null;
            for (int time : new int[]{18000, 114000}) {
                server.runCommand("time set " + time);
                context.waitTicks(10);
                var capture = context.takeScreenshot("clouds-moon-opaque-" + time);
                if (time == 18000) opaqueMoon = capture;
            }
            server.runCommand("time set 18000");
            context.runOnClient(client -> NaturalityConfig.get().clouds.base.opacityPercent = 50);
            context.waitTicks(10);
            var halfMoon = context.takeScreenshot("clouds-moon-half-opacity");
            if (centerVariation(java.util.Objects.requireNonNull(opaqueMoon)) > 2 || centerVariation(halfMoon) < 10)
                throw new AssertionError("100% cloud base must block the moon/stars; 50% must reveal them");
            context.runOnClient(client -> NaturalityConfig.get().clouds.base.opacityPercent = 100);
            for (int time : new int[]{15000, 21000}) {
                server.runCommand("time set " + time);
                server.runCommand("tp @a 0.5 164 0.5 " + (time == 15000 ? -90 : 90) + " -30");
                world.getConnection().waitForClientboundPackets();
                context.waitTicks(10);
                context.takeScreenshot("clouds-moon-moving-" + time);
            }
            // A shallow ocean reaches into edge fog while nearby clouds stay above that fade.
            server.runOnServer(s -> {
                for (int x = -4; x <= 4; x++) for (int z = -1; z <= 7; z++) s.overworld().getChunk(x, z);
            });
            server.runCommand("fill -64 178 -8 64 178 112 stone");
            server.runCommand("fill -64 179 -8 64 180 112 water");
            server.runCommand("tp @a 0.5 205 0.5 0 18");
            world.getConnection().waitForClientboundPackets();
            world.getConnection().waitForChunksRender();
            for (boolean oit : new boolean[]{false, true}) {
                context.runOnClient(client -> client.options.improvedTransparency().set(oit));
                for (String time : new String[]{"6000", "18000"}) {
                    server.runCommand("time set " + time);
                    context.waitTicks(10);
                    context.takeScreenshot("clouds-water-fog-" + oit + "-" + time);
                }
            }
            server.runCommand("time set 6000");
            context.runOnClient(client -> NaturalityConfig.get().clouds.enabled = false);
            context.waitTicks(8);
            context.takeScreenshot("clouds-vanilla-fallback");
            context.runOnClient(client -> {
                NaturalityConfig.get().clouds.enabled = true;
                NaturalityConfig.get().clouds.upper.enabled = false;
            });
            context.waitTicks(8);
            context.takeScreenshot("clouds-single-layer");
            var reload = new java.util.concurrent.atomic.AtomicReference<java.util.concurrent.CompletableFuture<Void>>();
            context.runOnClient(client -> reload.set(client.reloadResourcePacks()));
            context.waitFor(client -> reload.get().isDone());
            reload.get().join();
            context.waitFor(client -> client.gui.overlay() == null);
            context.waitTicks(5);
            context.takeScreenshot("clouds-after-reload");
            context.runOnClient(client -> client.gui.setScreen(new naturality.client.config.NaturalityCategoryScreen(
                null, naturality.client.config.NaturalityCategoryScreen.Category.CLOUDS)));
            context.waitTicks(2);
            context.takeScreenshot("clouds-settings");
            context.runOnClient(client -> client.gui.screen().onClose());
        } finally {
            context.runOnClient(client -> {
                NaturalityConfig.get().clouds = saved;
                NaturalityConfig.get().fog.enabled = savedFog;
                NaturalityConfig.get().save();
                client.options.cloudStatus().set(oldClouds[0]);
                if (client.gui.hud.isHidden() != old[0]) client.gui.hud.toggle();
                client.options.improvedTransparency().set(old[1]);
            });
        }
    }

    private static int centerVariation(java.nio.file.Path file) {
        try {
            var image = javax.imageio.ImageIO.read(file.toFile());
            int min = 255, max = 0;
            for (int y = image.getHeight() / 2 - 35; y <= image.getHeight() / 2 + 35; y++)
                for (int x = image.getWidth() / 2 - 35; x <= image.getWidth() / 2 + 35; x++) {
                    int red = (image.getRGB(x, y) >> 16) & 255;
                    min = Math.min(min, red);
                    max = Math.max(max, red);
                }
            return max - min;
        } catch (java.io.IOException exception) { throw new AssertionError(exception); }
    }

    private static void checkGeometry() {
        if (Math.abs(naturality.client.cloud.CloudMoonLighting.phaseStrength(0) - 1) > 0.0001
                || Math.abs(naturality.client.cloud.CloudMoonLighting.phaseStrength(4) - 0.1) > 0.0001)
            throw new AssertionError("Cloud moonlight must range from 100% at full moon to 10% at new moon");
        try (var renderer = new naturality.client.cloud.CloudLayerRenderer()) {
            var settings = new NaturalityConfig.CloudLayer();
            settings.fadingSides = false;
            var field = renderer.getClass().getDeclaredField("settings");
            field.setAccessible(true);
            field.set(renderer, settings);
            var encode = renderer.getClass().getDeclaredMethod("encodeFace", java.nio.ByteBuffer.class,
                int.class, int.class, net.minecraft.core.Direction.class, int.class);
            encode.setAccessible(true);
            var buffer = java.nio.ByteBuffer.allocate(32);
            for (var direction : net.minecraft.core.Direction.values()) encode.invoke(renderer, buffer, -17, 23, direction, 0);
            if (buffer.position() != 20) throw new AssertionError("Open clouds must contain five faces, no top");
            for (int i = 0; i < buffer.position(); i += 4) {
                int flags = buffer.get(i + 2) & 255;
                if ((flags & 7) == net.minecraft.core.Direction.UP.get3DDataValue()) throw new AssertionError("Top face survived");
                if ((buffer.get(i) << 1 | (flags >> 7 & 1)) != -17
                    || (buffer.get(i + 1) << 1 | (flags >> 6 & 1)) != 23) throw new AssertionError("Signed cloud coordinates changed");
            }
            settings.style = 1;
            buffer.clear();
            for (var direction : net.minecraft.core.Direction.values()) encode.invoke(renderer, buffer, 0, 0, direction, 0);
            if (buffer.position() != 24) throw new AssertionError("Solid style must retain the top");
            var clouds = new NaturalityConfig.Clouds();
            clouds.base.width = 0;
            clouds.upper = null;
            clouds.sanitize();
            if (clouds.base.width != 4 || clouds.upper.heightOffset != new NaturalityConfig.Clouds().upper.heightOffset) {
                throw new AssertionError("Cloud config validation failed");
            }
        } catch (ReflectiveOperationException exception) { throw new AssertionError(exception); }
    }
}

