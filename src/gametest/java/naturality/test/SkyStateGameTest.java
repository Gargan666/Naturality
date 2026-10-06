package naturality.test;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave;
import net.minecraft.world.level.Level;

/** End sky frames legitimately have no Overworld sky color, especially on login. */
public final class SkyStateGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        TestWorldSave save;
        try (var world = context.worldBuilder().create()) {
            save = world.getWorldSave();
            var server = world.getServer();
            server.runCommand("gamemode spectator @a");
            server.runCommand("execute in minecraft:the_end run tp @a 3000 110 0");
            context.waitTicks(30);
            context.runOnClient(client -> {
                check(client.level.dimension().equals(Level.END), "Fixture enters the End");
                // Remove the old Overworld color that normally masks the login crash.
                client.gameRenderer.gameRenderState().levelRenderState.skyRenderState.skyColor = null;
            });
            context.waitTicks(5);
            context.runOnClient(client -> check(
                client.gameRenderer.gameRenderState().levelRenderState.skyRenderState.skyColor == null,
                "End rendering must succeed without inventing an Overworld sky color"));
            server.runCommand("skyevent minecraft:the_end end_flashes 0");
            server.runCommand("execute in minecraft:the_end run tp @a 3000 110 0 0 90");
            context.waitTicks(5);
            context.takeScreenshot("end-purple-sky-void");
            server.runCommand("execute in minecraft:the_end run tp @a 3000 110 0 0 0");
            context.waitTicks(5);
            context.takeScreenshot("end-purple-sky-horizon");
            server.runCommand("execute in minecraft:the_end run tp @a 3000 110 0 0 -90");
            context.waitTicks(5);
            context.takeScreenshot("end-purple-sky-zenith");
            server.runCommand("execute in minecraft:overworld run tp @a 0 110 0");
            context.waitTicks(30);
            context.runOnClient(client -> check(
                client.gameRenderer.gameRenderState().levelRenderState.skyRenderState.skyColor != null,
                "Returning to the Overworld restores its normal sky color"));
            server.runCommand("execute in minecraft:the_nether run tp @a 0 110 0");
            context.waitTicks(20);
            server.runCommand("execute in minecraft:the_end run tp @a 3000 110 0");
            context.waitTicks(20);
        }
        context.runOnClient(client ->
            client.gameRenderer.gameRenderState().levelRenderState.skyRenderState.skyColor = null);
        try (var reopened = save.open()) {
            reopened.getConnection().waitForClientboundPackets();
            context.waitTicks(20);
            context.runOnClient(client -> check(client.level.dimension().equals(Level.END),
                "A world saved in the End reopens and renders without a previous Overworld frame"));
        }
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
