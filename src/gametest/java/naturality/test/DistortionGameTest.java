package naturality.test;

import java.util.UUID;
import naturality.NaturalityEffects;
import naturality.weather.EndGravity;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;

/** Live per-entity effect, expiry, networking, dimensions and saved rotation. */
public final class DistortionGameTest implements FabricClientGameTest {
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    @Override public void runTest(ClientGameTestContext context) {
        try (var world = context.worldBuilder().create()) {
            var server = world.getServer();
            server.runCommand("gamemode spectator @a");
            server.runCommand("tp @a 0 142 0 0 0");
            context.waitTicks(40);
            server.runCommand("fill -8 140 -8 8 150 8 air");
            server.runCommand("fill -8 140 -8 8 140 8 stone");
            server.runCommand("fill -8 150 -8 8 150 8 stone");
            server.runCommand("tp @a 0 141 0 0 0");
            server.runCommand("gamemode survival @a");
            UUID[] ids = new UUID[2];
            server.runOnServer(s -> {
                for (int i=0;i<2;i++) {
                    var cow=EntityTypes.COW.create(s.overworld(),EntitySpawnReason.COMMAND);
                    cow.setNoAi(true);cow.setPersistenceRequired();cow.setPos(3+i*2,141,3);
                    s.overworld().addFreshEntity(cow);ids[i]=cow.getUUID();
                    if(i==0)cow.addEffect(new MobEffectInstance(NaturalityEffects.DISTORTION,12000));
                }
            });
            server.runCommand("effect give @a naturality:distortion 9 0 true");
            context.waitTicks(20);
            server.runOnServer(s -> {
                var p=s.overworld().players().getFirst();
                check(EndGravity.inversion(p,false)>0 && EndGravity.inversion(p,false)<.5,"Effect rotation eases into inversion");
                var cow=(LivingEntity)s.overworld().getEntity(ids[0]);
                var output=TagValueOutput.createWithContext(ProblemReporter.DISCARDING,s.registryAccess());
                cow.saveWithoutId(output);
                var restored=EntityTypes.COW.create(s.overworld(),EntitySpawnReason.COMMAND);
                restored.load(TagValueInput.create(ProblemReporter.DISCARDING,s.registryAccess(),output.buildResult()));
                check(restored.hasEffect(NaturalityEffects.DISTORTION),"Potion effect is saved with the entity");
                check(Math.abs(EndGravity.inversion(restored,false)-EndGravity.inversion(cow,false))<1e-6,"Partial rotation survives entity save/load");
            });
            context.waitTicks(85);
            server.runOnServer(s -> {
                var level=s.overworld();var p=level.players().getFirst();
                check(EndGravity.inverted(p) && p.onGround(),"Affected Overworld player stands on a ceiling");
                check(EndGravity.inverted(level.getEntity(ids[0])),"Affected mob has reversed gravity");
                check(!EndGravity.inverted(level.getEntity(ids[1])) && level.getEntity(ids[1]).getGravity()>0,"Neighbor without the effect stays upright");
            });
            context.runOnClient(c -> check(c.gameRenderer.mainCamera().rotation().transform(new org.joml.Vector3f(0,1,0)).y<-.99,"Effect rotates the Overworld camera"));
            server.runCommand("effect clear @e[type=cow] naturality:distortion");
            context.waitTicks(25);
            server.runOnServer(s -> {
                float rotation=EndGravity.inversion(s.overworld().getEntity(ids[0]),false);
                check(rotation>0 && rotation<1,"Clearing a mob's effect eases upright");
                check(EndGravity.inverted(s.overworld().players().getFirst()),"Clearing another entity leaves the player inverted");
            });
            context.waitTicks(160);
            server.runOnServer(s -> {
                var p=s.overworld().players().getFirst();
                check(!p.hasEffect(NaturalityEffects.DISTORTION) && EndGravity.inversion(p,false)==0,"Effect expiry restores ordinary gravity");
                check(p.onGround() && Math.abs(p.getY()-141)<.05,"Expired effect lands the player on the floor");
            });
            context.runOnClient(c -> check(c.gameRenderer.mainCamera().rotation().transform(new org.joml.Vector3f(0,1,0)).y>.99,"Camera returns upright after expiry"));
            server.runCommand("gamemode spectator @a");
            server.runCommand("execute in minecraft:the_nether run tp @a 0 90 0");
            context.waitTicks(30);
            server.runOnServer(s -> {
                var nether=s.getLevel(Level.NETHER);
                var mob=EntityTypes.COW.create(nether,EntitySpawnReason.COMMAND);
                DistortionFixtures.invert(mob);
                check(EndGravity.inverted(mob) && mob.getGravity()<0,"Distortion also works in the Nether");
                var normal=EntityTypes.COW.create(nether,EntitySpawnReason.COMMAND);
                check(normal.getGravity()>0,"Unaffected Nether entities keep ordinary gravity");
            });
        }
    }
}
