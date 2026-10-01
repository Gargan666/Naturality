package naturality.test;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

/** Dense fog scenes make the prototype's density steps and screen grid easy to inspect. */
public final class PixelFogGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        int[] savedDistance = new int[1];
        context.runOnClient(client -> {
            savedDistance[0] = client.options.renderDistance().get();
            client.options.renderDistance().set(5);
        });
        try (var world = context.worldBuilder().create()) {
            var server = world.getServer();
            server.runCommand("gamemode creative @a");
            server.runCommand("time set noon");
            server.runCommand("fill -8 100 -4 8 100 24 smooth_quartz");
            server.runCommand("fill -8 101 24 8 110 24 stone_bricks");
            for (int z = 2; z <= 22; z += 4) {
                server.runCommand("fill -4 101 " + z + " -4 106 " + z + " bricks");
                server.runCommand("fill 4 101 " + z + " 4 106 " + z + " gold_block");
            }
            server.runCommand("tp @a 0 101 0 0 10");
            server.runCommand("effect give @a minecraft:blindness 60 0 true");
            world.getConnection().waitForClientboundPackets();
            context.waitTicks(30);
            world.getConnection().waitForChunksRender();
            context.takeScreenshot("pixel-fog-blindness");
            server.runCommand("effect clear @a");
            server.runCommand("fill -8 101 -4 8 108 24 water replace air");
            server.runCommand("tp @a 0 101 0 0 5");
            world.getConnection().waitForClientboundPackets();
            context.waitTicks(40);
            world.getConnection().waitForChunksRender();
            context.takeScreenshot("pixel-fog-underwater");
            // Remove water first so fluid updates cannot encase the camera in obsidian.
            server.runCommand("fill -8 101 -4 8 108 24 air");
            server.runCommand("fill -8 101 -4 8 108 24 lava");
            server.runCommand("tp @a 0 101 0 0 35");
            world.getConnection().waitForClientboundPackets();
            context.waitTicks(20);
            world.getConnection().waitForChunksRender();
            context.takeScreenshot("pixel-fog-lava");
            server.runCommand("gamemode spectator @a");
            server.runCommand("execute in minecraft:the_end run tp @a 0 150 0 0 0");
            context.waitFor(client -> client.level != null
                && client.level.dimension().equals(net.minecraft.world.level.Level.END));
            world.getConnection().waitForChunksRender();
            int panel = 0;
            for (int z : new int[]{40, 64, 72, 76, 79}) {
                int x = -24 + 12 * panel++;
                server.runOnServer(s -> {
                    var end = s.getLevel(net.minecraft.world.level.Level.END);
                    for (int cx = (x - 4) >> 4; cx <= (x + 4) >> 4; cx++) end.getChunk(cx, z >> 4);
                });
                server.runCommand("execute in minecraft:the_end run fill " + (x - 4) + " 144 " + z
                    + " " + (x + 4) + " 156 " + z + " end_stone");
            }
            world.getConnection().waitForClientboundPackets();
            context.waitTicks(20);
            world.getConnection().waitForChunksRender();
            context.runOnClient(client -> {
                if (!naturality.client.fog.EndFogTransparency.isActive(client.level, client.gameRenderer.mainCamera()))
                    throw new AssertionError("End atmosphere must use transparent fog");
            });
            // Separate panels reveal near, intermediate and full edge-fog opacity.
            server.runCommand("execute in minecraft:the_end run tp @a 0 150 0 0 0");
            world.getConnection().waitForClientboundPackets();
            context.waitTicks(10);
            world.getConnection().waitForChunksRender();
            context.runOnClient(client -> {
                if (!client.level.dimension().equals(net.minecraft.world.level.Level.END))
                    throw new AssertionError("Fog screenshot must remain in the End");
            });
            context.takeScreenshot("pixel-fog-end-transparent");
            var fogConfig = naturality.config.NaturalityConfig.get().fog;
            boolean originalEnabled = fogConfig.enabled;
            int originalPixelSize = fogConfig.pixelSize;
            try {
                fogConfig.enabled = false;
                reloadFog(context);
                context.runOnClient(client -> {
                    if (naturality.client.fog.EndFogTransparency.isActive(client.level, client.gameRenderer.mainCamera()))
                        throw new AssertionError("Disabling custom fog must restore colored End fog");
                });
                context.takeScreenshot("pixel-fog-end-disabled");
                fogConfig.enabled = true;
                fogConfig.pixelSize = 12;
                reloadFog(context);
                context.takeScreenshot("pixel-fog-end-large-pixels");
                // Outside the dragon arena the textured sky is visible, rather
                // than its boss-fog clear color. Verify both background paths.
                server.runCommand("execute in minecraft:the_end run tp @a 1000 150 0 0 0");
                world.getConnection().waitForClientboundPackets();
                world.getConnection().waitForChunksRender();
                int skyPanel = 0;
                for (int z : new int[]{40, 64, 72, 76, 79}) {
                    int x = 976 + 12 * skyPanel++;
                    server.runOnServer(s -> {
                        var end = s.getLevel(net.minecraft.world.level.Level.END);
                        for (int cx = (x - 4) >> 4; cx <= (x + 4) >> 4; cx++) end.getChunk(cx, z >> 4);
                    });
                    server.runCommand("execute in minecraft:the_end run fill " + (x - 4) + " 144 " + z
                        + " " + (x + 4) + " 156 " + z + " end_stone");
                }
                world.getConnection().waitForClientboundPackets();
                context.waitTicks(20);
                world.getConnection().waitForChunksRender();
                context.takeScreenshot("pixel-fog-end-sky");
            } finally {
                fogConfig.enabled = originalEnabled;
                fogConfig.pixelSize = originalPixelSize;
                reloadFog(context);
            }
            server.runCommand("effect give @a minecraft:blindness 30 0 true");
            world.getConnection().waitForClientboundPackets();
            context.waitTicks(5);
            context.runOnClient(client -> {
                if (naturality.client.fog.EndFogTransparency.isActive(client.level, client.gameRenderer.mainCamera()))
                    throw new AssertionError("End blindness must keep colored fog");
            });
            server.runCommand("effect clear @a");
            server.runCommand("execute in minecraft:overworld run tp @a 0 150 0 0 0");
            context.waitFor(client -> client.level != null
                && client.level.dimension().equals(net.minecraft.world.level.Level.OVERWORLD));
            context.runOnClient(client -> {
                if (!naturality.client.fog.EndFogTransparency.isActive(client.level, client.gameRenderer.mainCamera()))
                    throw new AssertionError("Overworld water must keep the atmosphere composite available");
            });
            server.runCommand("time set 6000");
            int overworldPanel = 0;
            for (int z : new int[]{40, 64, 72, 76, 79}) {
                int x = -24 + 12 * overworldPanel++;
                server.runOnServer(s -> {
                    for (int cx = (x - 4) >> 4; cx <= (x + 4) >> 4; cx++) s.overworld().getChunk(cx, z >> 4);
                });
                server.runCommand("fill " + (x - 4) + " 144 " + z + " " + (x + 4) + " 156 " + z + " sandstone");
            }
            world.getConnection().waitForClientboundPackets();
            context.waitTicks(30);
            world.getConnection().waitForChunksRender();
            context.takeScreenshot("pixel-fog-overworld-terrain-distance-panels");
        } finally {
            context.runOnClient(client -> client.options.renderDistance().set(savedDistance[0]));
        }
    }

    private static void reloadFog(ClientGameTestContext context) {
        var future = new java.util.concurrent.atomic.AtomicReference<java.util.concurrent.CompletableFuture<Void>>();
        context.runOnClient(client -> future.set(client.reloadResourcePacks()));
        context.waitFor(client -> future.get().isDone());
        future.get().join();
        context.waitFor(client -> client.gui.overlay() == null);
        context.waitTicks(5);
    }
}
