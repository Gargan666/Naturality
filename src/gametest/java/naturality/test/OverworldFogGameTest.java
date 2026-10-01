package naturality.test;

import java.nio.file.Path;
import javax.imageio.ImageIO;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

/** Opaque terrain must hide celestial bodies even when almost entirely sky-faded. */
public final class OverworldFogGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        var hideGui = new java.util.concurrent.atomic.AtomicBoolean();
        context.runOnClient(client -> { hideGui.set(client.gui.hud.isHidden()); if (!hideGui.get()) client.gui.hud.toggle(); });
        try (var world = context.worldBuilder().create()) {
            var server = world.getServer();
            server.runCommand("gamemode spectator @a");
            server.runOnServer(s -> s.setWeatherParameters(12000, 0, false, false));
            // Local shade must not change the open horizon, with either fog path.
            boolean originalFog = naturality.config.NaturalityConfig.get().fog.enabled;
            try {
                for (boolean customFog : new boolean[] {true, false}) {
                    context.runOnClient(client -> naturality.config.NaturalityConfig.get().fog.enabled = customFog);
                    reload(context);
                    server.runCommand("time set 6000");
                    server.runCommand("tp @a 0.5 150 0.5 0 0");
                    server.runCommand("fill -20 155 -20 20 155 20 air");
                    world.getConnection().waitForClientboundPackets();
                    context.waitTicks(15);
                    world.getConnection().waitForChunksRender();
                    Path open = context.takeScreenshot("shade-sky-open-" + customFog);
                    server.runCommand("fill -40 130 77 40 175 77 stone");
                    world.getConnection().waitForClientboundPackets();
                    context.waitTicks(15);
                    world.getConnection().waitForChunksRender();
                    Path terrainOpen = context.takeScreenshot("shade-terrain-open-" + customFog);
                    server.runCommand("fill -20 155 -20 20 155 20 stone");
                    world.getConnection().waitForClientboundPackets();
                    context.waitFor(client -> client.level.getBrightness(net.minecraft.world.level.LightLayer.SKY,
                        client.gameRenderer.mainCamera().blockPosition()) < 15);
                    context.waitTicks(15);
                    world.getConnection().waitForChunksRender();
                    context.runOnClient(client -> {
                        float factor = naturality.client.lighting.HardcoreDarkness.caveFog(client.level, client.gameRenderer.mainCamera());
                        if (factor >= 1) throw new AssertionError("Roof did not produce shade: pos=" + client.gameRenderer.mainCamera().blockPosition() + ", sky=" + client.level.getBrightness(net.minecraft.world.level.LightLayer.SKY, client.gameRenderer.mainCamera().blockPosition()) + ", block=" + client.level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK, client.gameRenderer.mainCamera().blockPosition()));
                    });
                    Path terrainShaded = context.takeScreenshot("shade-terrain-roof-" + customFog);
                    if (Math.abs(centerBrightness(terrainOpen) - centerBrightness(terrainShaded)) > 2)
                        throw new AssertionError("Local shade changed distant terrain fade (custom fog=" + customFog + ")");
                    server.runCommand("fill -40 130 77 40 175 77 air");
                    world.getConnection().waitForClientboundPackets();
                    context.waitTicks(15);
                    world.getConnection().waitForChunksRender();
                    Path shaded = context.takeScreenshot("shade-sky-roof-" + customFog);
                    if (Math.abs(centerBrightness(open) - centerBrightness(shaded)) > 1)
                        throw new AssertionError("Local shade changed sky brightness (custom fog=" + customFog + ")");
                }
            } finally {
                context.runOnClient(client -> naturality.config.NaturalityConfig.get().fog.enabled = originalFog);
                reload(context);
                server.runCommand("fill -20 155 -20 20 155 20 air");
            }
            server.runCommand("tp @a 0.5 150 0.5 0 -90");
            for (String body : new String[]{"sun", "moon"}) {
                server.runCommand("time set " + (body.equals("sun") ? "6000" : "18000"));
                server.runCommand("fill -40 230 -40 40 230 40 air");
                world.getConnection().waitForClientboundPackets();
                context.waitTicks(10);
                world.getConnection().waitForChunksRender();
                Path open = context.takeScreenshot("overworld-fog-" + body + "-open");
                server.runCommand("fill -40 230 -40 40 230 40 stone");
                world.getConnection().waitForClientboundPackets();
                context.waitTicks(10);
                world.getConnection().waitForChunksRender();
                Path blocked = context.takeScreenshot("overworld-fog-" + body + "-blocked");
                if (centerBrightness(open) < 100 || centerBrightness(blocked) >= centerBrightness(open) - 50)
                    throw new AssertionError("Fogged terrain must occlude the " + body);
            }
            server.runCommand("time set 13000");
            server.runCommand("gamerule minecraft:advance_time false");
            server.runCommand("tp @a 0.5 150 0.5 90 0");
            server.runCommand("fill -77 140 -40 -77 175 40 stone");
            world.getConnection().waitForClientboundPackets();
            context.waitTicks(10);
            world.getConnection().waitForChunksRender();
            context.takeScreenshot("overworld-fog-sunset-terrain");
            var horizon = new org.joml.Vector3f();
            context.runOnClient(client -> horizon.set(new net.minecraft.client.renderer.fog.environment.AtmosphericFogEnvironment()
                .getBaseColor(client.level, client.gameRenderer.mainCamera(), 5, 1.0F)));
            server.runCommand("tp @a 0.5 150 0.5 60 0");
            world.getConnection().waitForClientboundPackets();
            context.waitTicks(10);
            world.getConnection().waitForChunksRender();
            context.runOnClient(client -> {
                var turned = new net.minecraft.client.renderer.fog.environment.AtmosphericFogEnvironment()
                    .getBaseColor(client.level, client.gameRenderer.mainCamera(), 5, 1.0F);
                if (horizon.distance(turned) > 0.001F)
                    throw new AssertionError("Sunset horizon changed color when turning the camera");
            });
            context.takeScreenshot("overworld-fog-sunset-terrain-turned");
            server.runCommand("tp @a 0.5 150 0.5 90 0");
            server.runCommand("fill -77 140 -40 -77 175 40 air");
            world.getConnection().waitForClientboundPackets();
            context.waitTicks(10);
            world.getConnection().waitForChunksRender();
            context.takeScreenshot("overworld-fog-sunset-open");
        } finally {
            context.runOnClient(client -> { if (client.gui.hud.isHidden() != hideGui.get()) client.gui.hud.toggle(); });
        }
    }

    private static void reload(ClientGameTestContext context) {
        var future = new java.util.concurrent.atomic.AtomicReference<java.util.concurrent.CompletableFuture<Void>>();
        context.runOnClient(client -> future.set(client.reloadResourcePacks()));
        context.waitFor(client -> future.get().isDone());
        future.get().join();
        context.waitFor(client -> client.gui.overlay() == null);
        context.waitTicks(5);
    }

    private static int centerBrightness(Path path) {
        try {
            var image = ImageIO.read(path.toFile());
            int max = 0;
            for (int y = image.getHeight() / 2 - 30; y < image.getHeight() / 2 + 30; y++)
                for (int x = image.getWidth() / 2 - 30; x < image.getWidth() / 2 + 30; x++) {
                    int rgb = image.getRGB(x, y);
                    max = Math.max(max, (((rgb >> 16) & 255) + ((rgb >> 8) & 255) + (rgb & 255)) / 3);
                }
            return max;
        } catch (java.io.IOException exception) { throw new AssertionError(exception); }
    }
}






