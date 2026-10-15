package naturality.test;

import naturality.client.entity.FireballModelRenderer;
import naturality.client.entity.FireballShell;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.world.entity.projectile.hurtingprojectile.AbstractHurtingProjectile;
import net.minecraft.world.entity.projectile.hurtingprojectile.DragonFireball;

public final class FireballModelGameTest implements FabricClientGameTest {
    public static volatile int largeSmokeCount;
    @Override public void runTest(ClientGameTestContext context) {
        for (int step = 0; step < 924; step++) for (int face = 0; face < 6; face++) {
            for (int corner = 0; corner < 4; corner++) {
                int next = (corner + 1) % 4;
                float du = Math.abs(FireballShell.u(face,next,step) - FireballShell.u(face,corner,step)) * 44;
                float dv = Math.abs(FireballShell.v(face,next,step) - FireballShell.v(face,corner,step)) * 21;
                if (!((du < .0002F && Math.abs(dv-10) < .0002F)
                        || (dv < .0002F && Math.abs(du-10) < .0002F)))
                    throw new AssertionError("Fire shell face must retain square aligned pixels: " + face);
            }
        }
        try (var world = TestWorlds.disposable(context).create()) {
            var server = world.getServer();
            server.runCommand("gamemode spectator @a");
            server.runCommand("fill -8 100 -8 8 100 8 sea_lantern");
            server.runCommand("fill -8 101 -8 8 106 8 air");
            server.runCommand("tp @a 0 102 -4 0 0");
            largeSmokeCount = 0;
            server.runCommand("summon fireball -1 102 0 {Motion:[0.0d,0.0d,0.0d]}");
            server.runCommand("summon small_fireball 1 102 0 {Motion:[0.0d,0.0d,0.0d]}");
            server.runCommand("summon dragon_fireball 0 103.5 0 {Motion:[0.0d,0.0d,0.0d]}");
            context.waitTicks(15);
            context.runOnClient(client -> {
                int found = 0;
                for (var entity : TestFixtures.level(client).entitiesForRendering()) {
                    if (!(entity instanceof AbstractHurtingProjectile ball)) continue;
                    if (ball.isOnFire()) throw new AssertionError("Fireball must not automatically ignite");
                    var renderer = client.getEntityRenderDispatcher().getRenderer(ball);
                    if (!(renderer instanceof FireballModelRenderer<?>))
                        throw new AssertionError("Both fireball types must use the model renderer");
                    if (((FireballModelRenderer<?>)renderer).hasFireShell() == (ball instanceof DragonFireball))
                        throw new AssertionError("Only ordinary fireballs should have a fire shell");
                    var state = TestFixtures.require(renderer.createRenderState(ball, .5F), "state");
                    if (state.displayFireAnimation) throw new AssertionError("Separate entity flames must be suppressed");
                    found++;
                }
                if (found != 3) throw new AssertionError("Expected all three fireball types: " + found);
                if (FireballModelRenderer.fireOffset(1.99F) != 0
                        || FireballModelRenderer.fireOffset(2) != 1.0F / 44
                        || FireballModelRenderer.fireOffset(88) != 0)
                    throw new AssertionError("Fire UV must step by one texel and wrap");
                if (largeSmokeCount < 96) throw new AssertionError("Both fireballs must emit dense large smoke: " + largeSmokeCount);
            });
            context.takeScreenshot("fireball-models");
            context.waitTicks(7);
            context.takeScreenshot("fireball-models-spinning");
        }
    }
}


