package naturality.test;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import naturality.weather.WeatherSystem;

/** Water and a dry island must disappear into the same heavy-rain haze. */
public final class WaterRainFogGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        int[] distance = new int[1];
        boolean[] oit = new boolean[1];
        context.runOnClient(client -> {
            distance[0] = client.options.renderDistance().get();
            oit[0] = client.options.improvedTransparency().get();
            client.options.renderDistance().set(8);
        });
        try (var world = context.worldBuilder().create()) {
            var server = world.getServer();
            server.runCommand("gamemode spectator @a");
            server.runCommand("tp @a 0 105 0 0 16");
            server.runCommand("time set 6000");
            server.runCommand("fill -16 96 8 16 96 64 sandstone");
            server.runCommand("fill -16 97 8 16 102 64 water");
            server.runCommand("fill 4 97 24 10 103 30 sand");
            server.runCommand("weather minecraft:overworld rain 100");
            server.runCommand("weather minecraft:overworld temperature 50");
            world.getConnection().waitForClientboundPackets();
            context.waitTicks(240);
            // Rain/fluid updates continuously dirty meshes; global renderer
            // quiescence is not a valid wait condition in this scene.
            context.runOnClient(client -> {
                if (WeatherSystem.state(client.level).heavyRainFog() < 0.99F)
                    throw new AssertionError("Heavy rain must be active for water fog regression");
            });
            for (boolean mode : new boolean[]{false, true}) {
                context.runOnClient(client -> client.options.improvedTransparency().set(mode));
                context.waitTicks(8);
                context.takeScreenshot("water-heavy-rain-" + mode);
            }
        } finally {
            context.runOnClient(client -> {
                client.options.renderDistance().set(distance[0]);
                client.options.improvedTransparency().set(oit[0]);
            });
        }
    }
}
