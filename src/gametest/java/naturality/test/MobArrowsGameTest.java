package naturality.test;

import naturality.client.entity.EmbeddedArrowState;
import naturality.client.entity.MobArrowLayer;
import com.mojang.blaze3d.vertex.PoseStack;
import org.joml.Vector3f;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.world.entity.LivingEntity;

public final class MobArrowsGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        for (int axis = 0; axis < 3; axis++) {
            for (int sign : new int[] {-1, 1}) {
                var incoming = new Vector3f().setComponent(axis, sign);
                var face = MobArrowLayer.incomingFace(new Vector3f(), incoming,
                    new Vector3f(-1), new Vector3f(1), axis);
                if (face.get(axis) != -sign)
                    throw new AssertionError("Attachment must be on the shooter-facing surface for every approach direction");
            }
        }
        // Ghast enlargement/squash and nested limb rotations must not enlarge arrows.
        var pose = new PoseStack();
        pose.translate(2, 3, 4);
        pose.scale(-4, -6, 4);
        pose.rotate(new org.joml.Quaternionf().rotationXYZ(.4F, .7F, .2F));
        pose.translate(.3F, .2F, .1F);
        Vector3f attachment = pose.last().pose().getTranslation(new Vector3f());
        MobArrowLayer.removeModelScale(pose.last());
        var matrix = pose.last().pose();
        if (matrix.getTranslation(new Vector3f()).distance(attachment) > .00001F)
            throw new AssertionError("Arrow attachment must retain its scaled world position");
        var x = new Vector3f(matrix.m00(), matrix.m01(), matrix.m02());
        var y = new Vector3f(matrix.m10(), matrix.m11(), matrix.m12());
        var z = new Vector3f(matrix.m20(), matrix.m21(), matrix.m22());
        if (Math.abs(x.length() - 1) > .00001F || Math.abs(y.length() - 1) > .00001F
            || Math.abs(z.length() - 1) > .00001F || Math.abs(x.dot(y)) > .00001F
            || Math.abs(x.dot(z)) > .00001F || Math.abs(y.dot(z)) > .00001F)
            throw new AssertionError("Embedded arrows must retain regular size without model squash");
        try (var world = context.worldBuilder().create()) {
            var server = world.getServer();
            server.runCommand("gamemode spectator @a");
            server.runCommand("fill -8 100 -8 8 100 8 sea_lantern");
            server.runCommand("fill -8 101 -8 8 106 8 air");
            server.runCommand("time set midnight");
            server.runCommand("tp @a 0 102 -4 0 10");
            server.runCommand("summon pig -1 101 0 {NoAI:1b,Tags:[\"arrow_fixture\"]}");
            server.runCommand("summon zombie 1 101 0 {NoAI:1b,Tags:[\"arrow_fixture\"]}");
            server.runCommand("summon arrow -1 101.5 -2 {Motion:[0.0,0.0,1.0],damage:0.1d}");
            server.runCommand("summon arrow 1 102 -2 {Motion:[0.0,0.0,1.0],damage:0.1d}");
            context.waitTicks(30);
            context.runOnClient(client -> {
                int found = 0;
                for (var entity : TestFixtures.level(client).entitiesForRendering()) {
                    if (!(entity instanceof LivingEntity mob) || mob == client.player || mob.getArrowCount() == 0) continue;
                    var renderer = client.getEntityRenderDispatcher().getRenderer(mob);
                    var state = TestFixtures.require(renderer.createRenderState(mob, 1), "(EmbeddedArrowState)state");
                    if (((EmbeddedArrowState)state).naturality$arrowCount() != mob.getArrowCount())
                        throw new AssertionError("Embedded arrow count must reach the render state");
                    var hits = ((EmbeddedArrowState)state).naturality$impacts();
                    if (hits.isEmpty() || hits.get(0) == null || hits.get(0).point == null)
                        throw new AssertionError("Real arrow impact must synchronize and bind to the rendered mob model");
                    if (hits.get(0).hit.direction().z < .9)
                        throw new AssertionError("Arrow attachment must retain its incoming direction");
                    found++;
                }
                if (found != 2) throw new AssertionError("Both pig and zombie must retain a real arrow hit: " + found);
            });
            context.takeScreenshot("mob-embedded-arrows");
            server.runCommand("tp @a 0 102 4 180 10");
            context.waitTicks(5);
            context.takeScreenshot("mob-embedded-arrows-reverse");
            // Keep the live fixtures stationary while exercising the real death render path.
            for (int deathTick : new int[] {0, 10, 19}) {
                context.runOnClient(client -> {
                    for (var entity : TestFixtures.level(client).entitiesForRendering()) {
                        if (!(entity instanceof net.minecraft.world.entity.Mob mob)) continue;
                        mob.deathTime = deathTick;
                        var state = client.getEntityRenderDispatcher().getRenderer(mob).createRenderState(mob, 0);
                        int overlay = net.minecraft.client.renderer.entity.LivingEntityRenderer.getOverlayCoords(TestFixtures.require((net.minecraft.client.renderer.entity.state.LivingEntityRenderState) state, "(net.minecraft.client.renderer.entity.state.LivingEntityRenderState) state"), 0);
                        int progress = (overlay & 65535) >> 4;
                        int expected = Math.round(deathTick / 19F * 2047);
                        if (progress != expected || (overlay & 15) != 0)
                            throw new AssertionError("Death mask progress or native overlay changed: " + overlay);
                    }
                    var playerState = client.getEntityRenderDispatcher().getRenderer(client.player).createRenderState(client.player, 0);
                    if ((TestFixtures.require((naturality.client.entity.MobDeathState) playerState, "(naturality.client.entity.MobDeathState) playerState")).naturality$deathPixels() != 0)
                        throw new AssertionError("Player must not inherit mob death pixels");
                });
                context.takeScreenshot("mob-death-pixels-" + deathTick);
            }
            context.runOnClient(client -> {
                for (var entity : TestFixtures.level(client).entitiesForRendering()) {
                    if (!(entity instanceof net.minecraft.world.entity.Mob mob)) continue;
                    var shape = naturality.client.particle.DeathSmoke.shape(mob);
                    if (shape == null || shape.points().isEmpty()) throw new AssertionError("Missing posed death model");
                    for (var point : shape.points()) if (point.distanceTo(mob.position()) > 4)
                        throw new AssertionError("Smoke sample outside model: " + point);
                    naturality.client.particle.DeathSmoke.spawning = mob;
                    naturality.client.particle.DeathSmoke.index = 0;
                    try {
                        var puff = client.particleEngine.createParticle(net.minecraft.core.particles.ParticleTypes.POOF,
                            mob.getX(), mob.getY()+5, mob.getZ(), 0, 0, 0);
                        if (!(puff instanceof naturality.client.particle.DeathSmokeParticle))
                            throw new AssertionError("Death smoke must use posed provider");
                        double spawnY = puff.getBoundingBox().minY;
                        for (int tick = 0; tick < 8; tick++) puff.tick();
                        if (!puff.isAlive() || Math.abs(puff.getBoundingBox().minY - spawnY) > 1)
                            throw new AssertionError("Smoke must drift naturally from the posed spawn point");
                        if (Math.abs(puff.getBoundingBox().minY - (mob.getY()+5)) < 3)
                            throw new AssertionError("Smoke moved back toward the original spawn position");
                        puff.remove();
                    } finally { naturality.client.particle.DeathSmoke.spawning = null; }
                    mob.makePoofParticles();
                    mob.setInvisible(true);
                }
                if (naturality.client.particle.DeathSmoke.spawning != null)
                    throw new AssertionError("Death spawn context leaked");
                var ordinary = client.particleEngine.createParticle(net.minecraft.core.particles.ParticleTypes.POOF, 0, 101, 0, 0, 0, 0);
                if (ordinary instanceof naturality.client.particle.DeathSmokeParticle)
                    throw new AssertionError("Unrelated poof changed");
                TestFixtures.require(ordinary, "ordinary").remove();
            });
            // ParticleEngine integrates newly queued particles on the next tick.
            context.waitTicks(1);
            context.takeScreenshot("mob-death-smoke-cover");
            context.waitTicks(7);
            context.takeScreenshot("mob-death-smoke-drift");
            server.runCommand("summon spider -1 101 0 {NoAI:1b}");
            server.runCommand("summon cave_spider 1 101 0 {NoAI:1b}");
            context.waitTicks(5);
            for (int tick : new int[] {1,7,19}) {
                context.runOnClient(client -> {
                    for (var entity : TestFixtures.level(client).entitiesForRendering())
                        if (entity instanceof net.minecraft.world.entity.monster.spider.Spider spider) spider.deathTime=tick;
                });
                context.takeScreenshot("spider-death-lift-"+tick);
                context.runOnClient(client -> {
                    for (var entity : TestFixtures.level(client).entitiesForRendering())
                        if (entity instanceof net.minecraft.world.entity.monster.spider.Spider spider) {
                            var shape=naturality.client.particle.DeathSmoke.shape(spider);
                            if (shape==null) throw new AssertionError("Spider death model must be captured");
                            for (var point : shape.points()) if (point.y < spider.getY()-.05)
                                throw new AssertionError("Spider death model entered the ground: "+point);
                        }
                });
            }
        }
    }
}
