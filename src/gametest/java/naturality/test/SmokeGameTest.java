package naturality.test;

import naturality.client.particles.smoke.SmokeParticle;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.core.particles.ParticleTypes;

/** Exercise the registered vanilla providers in a loaded client world. */
public final class SmokeGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        try (var world = TestWorlds.disposable(context).create()) {
            world.getConnection().waitForClientboundPackets();
            context.runOnClient(client -> {
                for (var type : new net.minecraft.core.particles.SimpleParticleType[] {
                        ParticleTypes.SMOKE, ParticleTypes.LARGE_SMOKE}) {
                    var smoke = TestFixtures.require(client.particleEngine.createParticle(type, 0, 101, 0, 0, 0, 0), "smoke");
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
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
