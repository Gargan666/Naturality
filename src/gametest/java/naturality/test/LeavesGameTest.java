package naturality.test;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;

public final class LeavesGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        try (var world = context.worldBuilder().create()) {
            var server = world.getServer();
            server.runCommand("gamemode spectator @a");
            server.runCommand("fill -5 99 -5 5 99 5 stone");
            server.runCommand("fill 2 100 -2 2 104 2 stone");
            server.runCommand("fill -2 100 2 1 104 2 stone");
            server.runCommand("tp @a 0 101 -3 0 40");
            world.getConnection().waitForClientboundPackets();
            world.getConnection().waitForChunksRender();
            context.runOnClient(client -> {
                for (var type : new SimpleParticleType[] { ParticleTypes.CHERRY_LEAVES,
                        ParticleTypes.PALE_OAK_LEAVES, ParticleTypes.RED_POPLAR_LEAVES,
                        ParticleTypes.ORANGE_POPLAR_LEAVES, ParticleTypes.YELLOW_POPLAR_LEAVES }) {
                    var leaf = client.particleEngine.createParticle(type, 0, 100.01, 0, 0, 0, 0);
                    check(leaf != null, "Leaf provider must exist");
                    leaf.setParticleSpeed(0.01, -0.1, 0.01);
                    leaf.tick();
                    check(leaf.isAlive(), "Ground contact must not remove leaves");
                    var resting = leaf.getBoundingBox();
                    check(Math.abs(resting.minY - (100 + 1.0 / 512)) < 0.00001,
                        "Leaf must rest just above the support surface");
                    for (int i = 0; i < 49; i++) leaf.tick();
                    check(leaf.isAlive(), "Leaf must linger through the fade");
                    check(resting.equals(leaf.getBoundingBox()), "Landed leaves must stay still");
                    leaf.tick();
                    check(!leaf.isAlive(), "Leaf must expire after 40 resting and 10 fading ticks");
                    for (boolean xWall : new boolean[] { true, false }) {
                        var sliding = client.particleEngine.createParticle(type,
                            xWall ? 1.9 : 0.5, 102, xWall ? 0.5 : 1.9, 0, 0, 0);
                        // Press toward each wall while falling; neither blocked
                        // horizontal axis should remove or ground the particle.
                        for (int tick = 0; tick < 8; tick++) {
                            double previousY = sliding.getBoundingBox().minY;
                            sliding.setParticleSpeed(xWall ? 0.2 : 0.01, -0.1, xWall ? 0.01 : 0.2);
                            sliding.tick();
                            check(sliding.isAlive(), "Side collision must not remove leaves");
                            check(sliding.getBoundingBox().minY < previousY,
                                "Leaf must continue falling along a wall");
                            check((xWall ? sliding.getBoundingBox().maxX : sliding.getBoundingBox().maxZ) <= 2.000001,
                                "Sliding leaves must not pass through the wall");
                        }
                        for (int tick = 0; tick < 20; tick++) {
                            sliding.setParticleSpeed(xWall ? 0.2 : 0.01, -0.1, xWall ? 0.01 : 0.2);
                            sliding.tick();
                        }
                        check(sliding.isAlive(), "Leaf must land and linger at the foot of the wall");
                        check(Math.abs(sliding.getBoundingBox().minY - (100 + 1.0 / 512)) < 0.00001,
                            "Wall-sliding leaves must land on the upward-facing floor");
                        sliding.remove();
                    }
                }
                for (int i = 0; i < 30; i++) {
                    client.particleEngine.createParticle(ParticleTypes.CHERRY_LEAVES,
                        (i % 6 - 3) * 0.3, 100.1 + i / 6 * 0.25, 0.3 + i % 3 * 0.3, 0, 0, 0);
                }
            });
            context.waitTicks(20);
            context.takeScreenshot("leaves-tumbling-and-landed");
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
