package naturality.test;

import naturality.sky.*;
import naturality.client.sky.*;
import naturality.config.NaturalityServerConfig;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.CloudStatus;

public final class AuroraGameTest implements FabricClientGameTest {
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    @Override public void runTest(ClientGameTestContext context) {
        float mild = AuroraCycle.startChance(.8F, false, 50);
        float coldBiome = AuroraCycle.startChance(0, true, 50);
        float coldWeather = AuroraCycle.startChance(.8F, false, 0);
        float both = AuroraCycle.startChance(0, true, 0);
        check(mild < coldBiome && mild < coldWeather && both > coldBiome && both > coldWeather, "Both climate inputs increase chance");
        check(mild < .002 && both > .5, "Cold biome and weather strongly boost aurora starts");
        check(Math.abs(1 - Math.pow(1 - coldWeather, 2) - .15) < .002,
            "Temperature zero in a plains biome gives about 15% per idle minute");
        var cycle = new AuroraCycle(4);
        for (int i = 0; i < 800; i++) cycle.tick(true, 1);
        check(cycle.active() && cycle.strength() > 0, "Aurora can start without a day/night restriction");
        float frozen = cycle.strength(); cycle.tick(false, 1);
        check(cycle.strength() == frozen, "Weather gamerule freezes active evolution");
        var geometry = AuroraGeometry.tile(-2, 3, 2);
        check(geometry.equals(AuroraGeometry.tile(-2, 3, 2)), "World geometry is stable across visits");
        check(geometry.size() == 36 && AuroraGeometry.tile(-2, 3, 6).size() == 108,
            "Plane density follows the configured spline subdivision");
        boolean varied = false;
        for (int family = 0; family < 3; family++) {
            int start = family*12;
            check(geometry.get(start+3).b().equals(geometry.get(start+11).b()), "Branch joins main spline exactly");
            check(geometry.get(start+3).topB() == geometry.get(start+11).topB(), "Joining arms have the same height");
            for (int i = 0; i < 7; i++) {
                var left = geometry.get(start+i);
                var right = geometry.get(start+i+1);
                check(left.b().equals(right.a()) && left.topB() == right.topA(), "Main curtain has no cracks");
                varied |= left.topA() != left.topB();
            }
        }
        check(varied, "Curtain height varies between shared panel edges");
        var config = NaturalityServerConfig.get();
        var saved = config.skyEvents;
        boolean oldFog = naturality.config.NaturalityConfig.get().fog.enabled;
        int oldSegments = naturality.config.NaturalityConfig.get().effects.auroraSegments;
        var oldClouds = new CloudStatus[1];
        boolean[] oldOit = new boolean[1];
        config.skyEvents = SkyEvents.defaults();
        context.runOnClient(client -> {
            oldClouds[0] = client.options.cloudStatus().get();
            oldOit[0] = client.options.improvedTransparency().get();
            client.options.cloudStatus().set(CloudStatus.OFF);
            naturality.config.NaturalityConfig.get().effects.auroraSegments = 2;
        });
        try (var world = context.worldBuilder().create()) {
            var server = world.getServer();
            server.runCommand("gamemode spectator @a");
            server.runCommand("time set midnight");
            server.runOnServer(s -> s.setWeatherParameters(24000, 0, false, false));
            server.runCommand("tp @a 512 100 -160 0 -35");
            server.runCommand("skyevent minecraft:overworld aurora_borealis 20");
            context.waitTicks(110);
            context.runOnClient(client -> {
                check(SkyEventsClient.strength(client.level, SkyEventType.AURORA_BOREALIS) == 20, "Command synchronizes aurora");
                check(AuroraRenderer.strength() == 20 && AuroraRenderer.drawnPanels() > 0, "Curtains render");
            });
            context.takeScreenshot("aurora-strength-20");
            int[] defaultPanels = new int[1];
            context.runOnClient(client -> {
                defaultPanels[0] = AuroraRenderer.drawnPanels();
                naturality.config.NaturalityConfig.get().effects.auroraSegments = 6;
            });
            context.waitTicks(3);
            context.runOnClient(client -> {
                check(AuroraRenderer.drawnPanels() == defaultPanels[0] * 3,
                    "Changing plane density rebuilds the visible curtain meshes");
                naturality.config.NaturalityConfig.get().effects.auroraSegments = 2;
            });
            context.waitTicks(3);
            server.runCommand("tp @a 640 100 -32 0 -35");
            context.waitTicks(12);
            context.takeScreenshot("aurora-world-parallax");
            server.runCommand("skyevent minecraft:overworld aurora_borealis 7");
            context.waitTicks(70);
            context.takeScreenshot("aurora-strength-7");
            server.runCommand("time set noon");
            context.waitTicks(10);
            context.runOnClient(client -> check(AuroraRenderer.drawnPanels() > 0, "Aurora remains visible during daylight"));
            context.takeScreenshot("aurora-daytime");
            server.runCommand("time set midnight");
            server.runCommand("skyevent minecraft:overworld aurora_borealis 20");
            context.waitTicks(100);
            // A nearby opaque roof must occlude the world geometry, even without custom fog.
            server.runCommand("fill 624 116 -48 656 116 -16 stone");
            context.runOnClient(client -> naturality.config.NaturalityConfig.get().fog.enabled = false);
            context.waitTicks(20);
            context.takeScreenshot("aurora-roof-occlusion");
            server.runCommand("fill 624 116 -48 656 116 -16 air");
            context.runOnClient(client -> {
                naturality.config.NaturalityConfig.get().fog.enabled = true;
                client.options.cloudStatus().set(CloudStatus.FANCY);
                client.options.improvedTransparency().set(true);
            });
            context.waitTicks(20);
            context.takeScreenshot("aurora-clouds-oit");
            context.runOnClient(client -> client.options.cloudStatus().set(CloudStatus.OFF));
            server.runCommand("tp @a 640 650 -32 0 25");
            context.waitTicks(15);
            context.takeScreenshot("aurora-from-above");
            server.runCommand("tp @a 640 100 -32 0 -35");
            context.waitTicks(15);
            var reload = new java.util.concurrent.atomic.AtomicReference<java.util.concurrent.CompletableFuture<Void>>();
            context.runOnClient(client -> reload.set(client.reloadResourcePacks()));
            context.waitFor(client -> reload.get().isDone());
            check(!reload.get().isCompletedExceptionally(), "Resource reload succeeds");
            context.waitTicks(100);
            context.runOnClient(client -> check(AuroraRenderer.drawnPanels() > 0, "Renderer survives resource reload"));
            server.runCommand("skyevent minecraft:overworld aurora_borealis 0");
            context.waitTicks(100);
            context.runOnClient(client -> check(AuroraRenderer.drawnPanels() == 0, "Zero override fades all curtains away"));
            server.runCommand("skyevent minecraft:overworld aurora_borealis auto");
            server.runOnServer(s -> check(!config.skyEvents.get("minecraft:overworld").get("aurora_borealis").override, "Auto releases override"));
        } finally {
            config.skyEvents = saved; config.save();
            naturality.config.NaturalityConfig.get().fog.enabled = oldFog;
            naturality.config.NaturalityConfig.get().effects.auroraSegments = oldSegments;
            context.runOnClient(client -> {
                client.options.cloudStatus().set(oldClouds[0]);
                client.options.improvedTransparency().set(oldOit[0]);
            });
        }
    }
}
