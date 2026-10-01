package naturality.test;

import naturality.config.NaturalityConfig;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

/** Captures Naturality's animated sun beams in a clear Overworld sunset. */
public final class SunsetGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        boolean[] previous = new boolean[2];
        context.runOnClient(client -> {
            previous[0] = NaturalityConfig.get().effects.sunBeams;
            previous[1] = client.gui.hud.isHidden();
            NaturalityConfig.get().effects.sunBeams = false;
            if (!previous[1]) client.gui.hud.toggle();
        });
        try (var world = context.worldBuilder().create()) {
            var server = world.getServer();
            server.runCommand("gamemode spectator @a");
            server.runCommand("gamerule minecraft:advance_time false");
            server.runOnServer(s -> s.setWeatherParameters(12000, 0, false, false));
            server.runCommand("time set 13000");
            // Yaw 90 faces west in Minecraft, toward the setting sun.
            server.runCommand("tp @a 0.5 150 0.5 90 0");
            world.getConnection().waitForClientboundPackets();
            world.getConnection().waitForChunksRender();
            context.waitTicks(20);
            context.takeScreenshot("sunset-sun-base");
            context.runOnClient(client -> NaturalityConfig.get().effects.sunBeams = true);
            context.waitTicks(10);
            context.takeScreenshot("sunset-sun-beams");
        } finally {
            context.runOnClient(client -> {
                NaturalityConfig.get().effects.sunBeams = previous[0];
                NaturalityConfig.get().save();
                if (client.gui.hud.isHidden() != previous[1]) client.gui.hud.toggle();
            });
        }
    }
}
