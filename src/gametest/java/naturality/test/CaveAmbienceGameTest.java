package naturality.test;

import naturality.client.sound.CaveAmbience;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

public final class CaveAmbienceGameTest implements FabricClientGameTest {
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    @Override public void runTest(ClientGameTestContext context) {
        try (var world = context.worldBuilder().create()) {
            var server = world.getServer();
            server.runCommand("gamemode spectator @a");
            server.runCommand("tp @a 0 88 0");
            context.waitTicks(30);
            server.runCommand("fill -12 86 -12 12 110 12 stone");
            server.runCommand("fill -11 87 -11 11 102 11 air");
            world.getConnection().waitForClientboundPackets();
            context.waitTicks(80);
            context.runOnClient(c -> {
                check(CaveAmbience.isLargeCave(TestFixtures.level(c), TestFixtures.player(c).getEyePosition(), TestFixtures.player(c)),
                    "Broad, tall underground chamber qualifies");
                check(CaveAmbience.gain() > .99F, "Large chamber fades up to full gain");
                int loaded = TestFixtures.level(c).getChunkSource().getLoadedChunksCount();
                check(!CaveAmbience.isLargeCave(TestFixtures.level(c), new net.minecraft.world.phys.Vec3(1000000, 88, 1000000), TestFixtures.player(c)),
                    "Unloaded terrain cannot qualify as a cave");
                check(TestFixtures.level(c).getChunkSource().getLoadedChunksCount() == loaded, "Cave detection does not load chunks");
            });
            server.runCommand("fill -11 87 -11 11 102 11 stone");
            server.runCommand("fill -1 87 -11 1 102 11 air");
            world.getConnection().waitForClientboundPackets();
            context.waitTicks(25);
            context.runOnClient(c -> {
                check(!CaveAmbience.isLargeCave(TestFixtures.level(c), TestFixtures.player(c).getEyePosition(), TestFixtures.player(c)), "Narrow tunnel is rejected");
                check(CaveAmbience.gain() > 0 && CaveAmbience.gain() < .9F, "Entering a tunnel fades gradually");
            });
            context.waitTicks(65);
            context.runOnClient(_ -> check(CaveAmbience.gain() == 0, "Small cave becomes silent"));
            server.runCommand("fill -11 87 -11 11 102 11 stone");
            server.runCommand("fill -11 87 -11 11 90 11 air");
            world.getConnection().waitForClientboundPackets();
            context.waitTicks(20);
            context.runOnClient(c -> check(!CaveAmbience.isLargeCave(TestFixtures.level(c), TestFixtures.player(c).getEyePosition(), TestFixtures.player(c)),
                "Broad but low ceiling chamber is rejected"));
            server.runCommand("tp @a 0 114 0");
            context.waitTicks(20);
            context.runOnClient(c -> check(!CaveAmbience.isLargeCave(TestFixtures.level(c), TestFixtures.player(c).getEyePosition(), TestFixtures.player(c)),
                "Open surface is rejected"));
            server.runCommand("execute in minecraft:the_nether run tp @a 0 100 0");
            context.waitTicks(40);
            context.runOnClient(_ -> check(CaveAmbience.gain() == 0, "Dimension change clears ambience"));
        }
    }
}
