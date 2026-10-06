package naturality.test;

import java.util.UUID;
import naturality.weather.CeilingPathNavigation;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class EndMobAiGameTest implements FabricClientGameTest {
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static <T extends PathfinderMob> T add(net.minecraft.server.level.ServerLevel level, EntityType<T> type,
            double x, double feet, double z) {
        var mob = type.create(level, EntitySpawnReason.COMMAND);
        mob.removeFreeWill(); DistortionFixtures.invert(mob); mob.setPersistenceRequired(); mob.setSilent(true); mob.setPermanentlyInvulnerable(true);
        mob.setPos(x, feet - mob.getBbHeight(), z); mob.setOnGround(true);
        level.addFreshEntity(mob);
        return mob;
    }
    @Override public void runTest(ClientGameTestContext context) {
        try (var world = context.worldBuilder().create()) {
            var server = world.getServer();
            server.runOnServer(s -> s.getLevel(Level.END).setDragonFight(null));
            server.runCommand("gamerule minecraft:advance_weather false");
            server.runCommand("gamemode spectator @a");
            server.runCommand("execute in minecraft:the_end run tp @a 0 144 0");
            context.waitTicks(40);
            server.runCommand("execute in minecraft:the_end run fill -14 130 -12 14 130 12 end_stone");
            server.runCommand("execute in minecraft:the_end run fill -14 150 -12 14 150 12 end_stone");
            server.runCommand("execute in minecraft:the_end run fill 0 145 -2 0 149 2 end_stone");
            server.runCommand("execute in minecraft:the_end run fill 0 149 7 8 149 10 end_stone");

            context.waitTicks(90);
            UUID[] ids = new UUID[5];
            PathNavigation[] originals = new PathNavigation[3];
            server.runOnServer(s -> {
                var end = s.getLevel(Level.END);
                var hunter = add(end, EntityTypes.ENDERMAN, -7.5, 150, .5);
                var target = add(end, EntityTypes.COW, 7.5, 150, .5); target.setNoAi(true); target.setPermanentlyInvulnerable(false);
                target.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(1000); target.setHealth(1000); 
                var jumper = add(end, EntityTypes.COW, -5.5, 150, 8.5);
                var wanderer = add(end, EntityTypes.COW, -8.5, 150, -8.5);
                var immune = add(end, EntityTypes.PIG, -6.5, 132, -6.5);
                ids[0] = hunter.getUUID(); ids[1] = target.getUUID(); ids[2] = jumper.getUUID();
                ids[3] = wanderer.getUUID(); ids[4] = immune.getUUID();
                originals[0] = hunter.getNavigation(); originals[1] = jumper.getNavigation(); originals[2] = immune.getNavigation();
            });
            context.waitTicks(8);
            server.runOnServer(s -> {
                var end = s.getLevel(Level.END);
                var hunter = (PathfinderMob) end.getEntity(ids[0]); var target = (LivingEntity) end.getEntity(ids[1]);
                check(hunter.getNavigation() instanceof CeilingPathNavigation, "Inverted walking mob uses ceiling navigation");
                var path = hunter.getNavigation().createPath(target, 0);
                check(path != null && path.canReach(), "Enderman can path to a target beneath the same island: pos=" + hunter.position() + " grounded=" + hunter.onGround() + " target=" + target.position() + " path=" + path + " end=" + (path == null ? null : path.getEndNode()) + " surface=" + CeilingPathNavigation.surfaceAt(hunter, hunter.blockPosition(), 1));
                var multi = hunter.getNavigation().createPath(java.util.Set.of(target.blockPosition(), target.blockPosition().above()), 0);
                check(multi != null && multi.canReach(), "Several destinations on one ceiling deduplicate without crashing A*");
                boolean detour = false;
                for (int i = 0; i < path.getNodeCount(); i++) {
                    var node = path.getNode(i);
                    check(node.y >= 146, "Path stays under the ceiling instead of selecting the floor");
                    if (Math.abs(node.z) >= 3) detour = true;
                }
                check(detour, "Path routes around a hanging obstacle");
                hunter.getGoalSelector().addGoal(1, new MeleeAttackGoal(hunter, 1, true)); hunter.setTarget(target);
                var jumper = (PathfinderMob) end.getEntity(ids[2]);
                check(jumper.getNavigation().moveTo(5.5, 149 - jumper.getBbHeight(), 8.5, 0, 1.2), "Cow finds a route onto a lower ceiling");
                var noRoof = hunter.getNavigation().createPath(new BlockPos(25, 147, 0), 0);
                check(noRoof == null || !noRoof.canReach(), "Routes cannot cross unsupported void");
                var wanderer = (PathfinderMob) end.getEntity(ids[3]);
                wanderer.setPos(-13.95, 150.0 - wanderer.getBbHeight(), -8.5); wanderer.setOnGround(true);
                var edgePath = wanderer.getNavigation().createPath(new BlockPos(-10, 148, -9), 0);
                check(edgePath != null && edgePath.canReach(), "A mob partly over a ceiling edge can path back onto it");
                wanderer.setPos(-8.5, 150.0 - wanderer.getBbHeight(), -8.5); wanderer.setOnGround(true);
                check(LandRandomPos.getPos(wanderer, 8, 7) != null, "Normal wandering can select a ceiling destination");
                var wander = new WaterAvoidingRandomStrollGoal(wanderer, 1); wander.setInterval(1); wander.trigger();
                wanderer.getGoalSelector().addGoal(1, wander);
                check(((Mob) end.getEntity(ids[4])).getNavigation() == originals[2], "Gravity-exempt mob keeps its navigator");
            });
            context.waitTicks(160);
            server.runOnServer(s -> {
                var end = s.getLevel(Level.END);
                var hunter = (Mob) end.getEntity(ids[0]); var target = end.getEntity(ids[1]);
                check(hunter.distanceTo(target) < 2.5, "Real melee AI follows its ceiling route: " + hunter.position() + " target=" + hunter.getTarget() + " path=" + hunter.getNavigation().getPath() + " speed=" + hunter.getSpeed() + " cow=" + end.getEntity(ids[2]).position());
                var jumper = (Mob) end.getEntity(ids[2]);
                check(jumper.getX() > 4.5 && Math.abs(jumper.getBoundingBox().maxY - 149) < .05,
                    "Move/jump controls traverse the one-block underside step: " + jumper.position());
                var wanderer = (Mob) end.getEntity(ids[3]);
                check(wanderer.position().distanceTo(new Vec3(-8.5, 150 - wanderer.getBbHeight(), -8.5)) > 1,
                    "Ordinary wandering goal moves while inverted");
                for (int i : new int[]{0, 2, 3}) {
                    var mob = (Mob) end.getEntity(ids[i]);
                    check(mob.onGround() && end.noCollision(mob, mob.getBoundingBox().deflate(1e-6)), "Ceiling AI remains supported and outside terrain");
                    mob.removeFreeWill(); mob.getNavigation().stop();
                }
            });
            context.takeScreenshot("end-mob-ai-ceiling");
            server.runCommand("effect clear @e naturality:distortion");
            context.waitTicks(120);
            server.runOnServer(s -> {
                var end = s.getLevel(Level.END);
                check(((Mob) end.getEntity(ids[0])).getNavigation() == originals[0], "Enderman regains its original navigator after a flip");
                var jumper = (Mob) end.getEntity(ids[2]);
                check(jumper.getNavigation() == originals[1], "Ordinary mob regains its original navigator");
                check(jumper.onGround() && jumper.getY() < 132, "Ordinary mob lands and resumes floor movement");
                check(jumper.getNavigation().moveTo(8.5, 131, 8.5, 1), "Restored floor navigation still finds paths");
            });
        }
    }
}
