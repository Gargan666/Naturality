package naturality.test;

import naturality.client.particle.AnimatedFlameParticle;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class FlameGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        try (var world = context.worldBuilder().create()) {
            var server = world.getServer();
            server.runCommand("gamemode spectator @a");
            server.runCommand("fill -3 99 -3 3 99 3 stone");
            server.runCommand("setblock 0 100 0 torch");
            server.runCommand("setblock 1 100 0 soul_torch");
            server.runCommand("tp @a 0 101 -2 0 35");
            world.getConnection().waitForClientboundPackets();
            world.getConnection().waitForChunksRender();
            context.runOnClient(client -> {
                for (var type : new SimpleParticleType[] {ParticleTypes.FLAME, ParticleTypes.SMALL_FLAME,
                        ParticleTypes.SOUL_FIRE_FLAME}) {
                    var particle = client.particleEngine.createParticle(type, 0, 101, 0, 0, 0, 0);
                    check(particle instanceof AnimatedFlameParticle, "Animated flame provider must be registered");
                    var flame = (AnimatedFlameParticle) particle;
                    float size = flame.getQuadSize(0);
                    Quaternionf pose = new Quaternionf();
                    flame.getFacingCameraMode().setRotation(pose, client.gameRenderer.mainCamera(), 0);
                    check(pose.transform(new Vector3f(0, 1, 0)).distance(new Vector3f(0, 1, 0)) < 0.00001F,
                        "Flame must retain world up with a pitched camera");
                    check(Math.abs(pose.lengthSquared() - 1) < 0.00001F, "Camera pitch must not distort flame size");
                    check(flame.getLifetime() == 25, "Five animation frames must last 25 ticks");
                    for (int i = 0; i < 24; i++) {
                        flame.tick();
                        check(flame.isAlive(), "Flame must survive through the final frame");
                        check(flame.getQuadSize(0.5F) == size, "Flame must not shrink");
                    }
                    flame.tick();
                    check(!flame.isAlive(), "Flame must disappear when its animation finishes");
                }
            });
            context.waitTicks(10);
            context.takeScreenshot("flame-world-up");
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}

