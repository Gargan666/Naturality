package naturality.test;

import naturality.client.entity.EmbeddedArrowState;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.world.entity.LivingEntity;

public final class MobArrowsGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
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
                for (var entity : client.level.entitiesForRendering()) {
                    if (!(entity instanceof LivingEntity mob) || mob == client.player || mob.getArrowCount() == 0) continue;
                    var renderer = client.getEntityRenderDispatcher().getRenderer(mob);
                    var state = renderer.createRenderState(mob, 1);
                    if (((EmbeddedArrowState)state).naturality$arrowCount() != mob.getArrowCount())
                        throw new AssertionError("Embedded arrow count must reach the render state");
                    found++;
                }
                if (found != 2) throw new AssertionError("Both pig and zombie must retain a real arrow hit: " + found);
            });
            context.takeScreenshot("mob-embedded-arrows");
            server.runCommand("tp @a 0 102 4 180 10");
            context.waitTicks(5);
            context.takeScreenshot("mob-embedded-arrows-reverse");
        }
    }
}
