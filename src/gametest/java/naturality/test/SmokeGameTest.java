package naturality.test;

import naturality.client.particle.SmokeParticle;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.core.particles.ParticleTypes;

/** Exercise the registered vanilla providers in a loaded client world. */
public final class SmokeGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        try (var world = context.worldBuilder().create()) {
            var server = world.getServer();
            server.runCommand("gamemode spectator @a");
            server.runCommand("tp @a 0 102 -8 0 0");
            server.runCommand("fill -5 99 -5 5 99 5 stone");
            server.runCommand("setblock -2 100 0 torch");
            server.runCommand("setblock 2 99 0 netherrack");
            server.runCommand("setblock 2 100 0 fire");
            world.getConnection().waitForClientboundPackets();
            world.getConnection().waitForChunksRender();
            context.runOnClient(client -> {
                ParticleWindChecks.run(client);
                for (var type : new net.minecraft.core.particles.SimpleParticleType[] {
                        ParticleTypes.SMOKE, ParticleTypes.LARGE_SMOKE}) {
                    var smoke = client.particleEngine.createParticle(type, 0, 101, 0, 0, 0, 0);
                    check(smoke instanceof SmokeParticle, "Vanilla smoke must use the replacement provider");
                    double start = smoke.getBoundingBox().minY;
                    int duration = smoke.getLifetime();
                    check(duration >= 5 && duration <= 60 && duration % 5 == 0,
                        "Lifetime must match the remaining animation frames");
                    for (int i = 0; i < duration - 1; i++) smoke.tick();
                    check(smoke.isAlive(), "Smoke must display its final frame for the full frame duration");
                    check(smoke.getBoundingBox().minY > start, "Smoke must rise while animating");
                    smoke.tick();
                    check(!smoke.isAlive(), "Smoke must disappear when the final frame finishes");
                }
            });
            context.waitTicks(120);
            context.takeScreenshot("smoke-torch-and-fire");
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
