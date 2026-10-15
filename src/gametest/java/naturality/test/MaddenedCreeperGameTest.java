package naturality.test;

import naturality.entity.MaddenedCreeper;
import naturality.entity.MaddenedCreepers;
import naturality.client.entity.MaddenedCreeperRenderer;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.animal.pig.Pig;
import naturality.NaturalityEffects;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.util.ProblemReporter;

public final class MaddenedCreeperGameTest implements FabricClientGameTest {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    public void runTest(ClientGameTestContext context) {
        float min = 1, max = 0;
        for (int age = 0; age < 100; age++) {
            float pulse = MaddenedCreeperRenderer.idlePulse(age);
            min = Math.min(min, pulse); max = Math.max(max, pulse);
        }
        check(min >= .219F && max <= .421F && max - min > .19F, "Continuous bounded later fuse pulse");
        try (var world = context.worldBuilder().create()) {
            var server = world.getServer();
            server.runCommand("gamemode spectator @a");
            server.runCommand("time set noon");
            server.runCommand("fill -8 100 -8 24 100 8 stone");
            server.runCommand("tp @a 0 102 -5 0 10");
            server.runCommand("summon naturality:maddened_creeper 0 101 0 {NoAI:1b,Silent:1b,PersistenceRequired:1b}");
            context.waitTicks(30);
            context.runOnClient(client -> {
                int count = 0;
                for (var e : TestFixtures.level(client).entitiesForRendering()) if (e instanceof MaddenedCreeper creeper) {
                    count++;
                    check(creeper.isAlive() && creeper.getSwelling(1) == 0, "Idle pulse does not prime gameplay fuse");
                    var renderer = (MaddenedCreeperRenderer)client.getEntityRenderDispatcher().getRenderer(creeper);
                    var state = renderer.createRenderState(creeper, 1);
                    check(renderer.getTextureLocation(state).getPath().equals("textures/entity/maddened_creeper.png"), "Artist texture selected");
                }
                check(count == 1, "Maddened creeper summon and client renderer");
            });
            context.takeScreenshot("maddened-creeper-custom-model");
            server.runCommand("time set midnight");
            server.runCommand("tp @a 0 102 4 180 10");
            context.waitTicks(10);
            context.takeScreenshot("maddened-creeper-emissive-night");
            server.runOnServer(s -> {
                var level = s.overworld();
                var mob = new MaddenedCreeper(MaddenedCreepers.TYPE, level);
                mob.setPos(12, 101, 0);
                check(Math.abs(mob.getAttributeValue(Attributes.MOVEMENT_SPEED) - .35) < .001, "Faster movement attribute");
                var saved = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, s.registryAccess());
                mob.saveWithoutId(saved);
                var restored = new MaddenedCreeper(MaddenedCreepers.TYPE, level);
                restored.load(TagValueInput.create(ProblemReporter.DISCARDING, s.registryAccess(), saved.buildResult()));
                var target = new Pig(EntityTypes.PIG, level);
                target.setPos(14, 101, 0);
                mob.setTarget(target); mob.setOnGround(true); mob.setSwellDir(1);
                mob.tick();
                check(mob.getDeltaMovement().x > .1 && mob.getDeltaMovement().y > .1, "Leap toward target when priming");
                // Compare actual vanilla explosion timing after restoring entity NBT.
                restored.setNoAi(true); restored.ignite();
                var regular = new Creeper(EntityTypes.CREEPER, level);
                regular.setPos(20, 101, 0); regular.setNoAi(true); regular.ignite();
                for (int i = 0; i < 16; i++) { restored.tick(); regular.tick(); }
                check(restored.isRemoved() && !regular.isRemoved(), "16 tick fuse survives save/load; vanilla remains 30 ticks");
                AreaEffectCloud cloud = null;
                for (var e : level.getAllEntities()) if (e instanceof AreaEffectCloud found) cloud = found;
                check(cloud != null && cloud.getRadius() > 2, "Explosion creates lingering cloud without prior potion effects");
                cloud=TestFixtures.require(cloud, "cloud");
                var victim = new Pig(EntityTypes.PIG, level);
                victim.setPos(cloud.getX(), cloud.getY(), cloud.getZ());
                victim.setNoAi(true); level.addFreshEntity(victim);
                // ServerLevel advances tickCount before calling Entity.tick.
                for (int i = 0; i < 20; i++) { cloud.tickCount++; cloud.tick(); }
                check(victim.hasEffect(NaturalityEffects.INSANITY), "Lingering cloud applies Insanity");
                victim.discard(); cloud.discard();
            });
        }
    }
}
