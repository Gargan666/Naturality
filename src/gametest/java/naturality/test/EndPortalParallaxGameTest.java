package naturality.test;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

/** Render the actual portal pipeline from translated and tilted viewpoints. */
public final class EndPortalParallaxGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        try (var world = context.worldBuilder().create()) {
            var server = world.getServer();
            server.runCommand("gamemode spectator @a");
            server.runCommand("time set noon");
            server.runCommand("fill -5 99 -5 5 99 5 smooth_quartz");
            server.runCommand("fill -2 100 -2 2 100 2 end_portal_frame");
            server.runCommand("fill -1 100 -1 1 100 1 end_portal");
            server.runCommand("tick freeze");
            String[] views = {"0.5 103 -3 0 48", "1.5 103 -3 0 48",
                "0.5 105 0.5 0 90", "0.5 101 -4 0 15", "0.5 101.5 -1.5 0 48",
                "0.5 108 -7.5 0 48", "3.0 101 -3.0 45 28", "3.02 101 -3.02 45 28"};
            String[] names = {"oblique", "translated", "overhead", "grazing", "close", "distant", "border-angle", "border-angle-shifted"};
            for (int i = 0; i < views.length; i++) {
                server.runCommand("tp @a " + views[i]);
                world.getConnection().waitForClientboundPackets();
                context.waitTicks(8);
                world.getConnection().waitForChunksRender();
                context.takeScreenshot("end-portal-" + names[i]);
            }
            context.runOnClient(client -> client.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK));
            context.waitTicks(8);
            context.takeScreenshot("end-portal-third-person");
            context.runOnClient(client -> client.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON));
            server.runCommand("tp @a 0.5 102 -2 0 48");
            world.getConnection().waitForClientboundPackets();
            context.waitTicks(8);
            boolean[] previous = new boolean[2];
            context.runOnClient(client -> {
                previous[0] = naturality.config.NaturalityConfig.get().portalChanges.glowEffect;
                previous[1] = client.options.improvedTransparency().get();
                naturality.config.NaturalityConfig.get().portalChanges.glowEffect = false;
            });
            try {
                context.waitTicks(3);
                context.takeScreenshot("end-portal-wall-no-glow");
                context.runOnClient(client -> {
                    naturality.config.NaturalityConfig.get().portalChanges.glowEffect = true;
                    client.options.improvedTransparency().set(true);
                });
                context.waitTicks(8);
                context.takeScreenshot("end-portal-border-improved-transparency");
                server.runCommand("setblock 0 101 0 stone");
                server.runCommand("setblock 0 100 2 bricks");
                world.getConnection().waitForClientboundPackets();
                context.waitTicks(8);
                world.getConnection().waitForChunksRender();
                context.takeScreenshot("end-portal-border-occlusion-and-bricks");
                server.runCommand("setblock 0 100 0 air");
                world.getConnection().waitForClientboundPackets();
                context.waitTicks(8);
                world.getConnection().waitForChunksRender();
                context.takeScreenshot("end-portal-border-gap");
            } finally {
                context.runOnClient(client -> {
                    naturality.config.NaturalityConfig.get().portalChanges.glowEffect = previous[0];
                    client.options.improvedTransparency().set(previous[1]);
                });
            }
            server.runCommand("tick unfreeze");
        }
    }
}
