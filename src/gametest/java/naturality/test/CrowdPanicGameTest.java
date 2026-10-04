package naturality.test;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.behavior.VillagerPanicTrigger;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.core.BlockPos;

public final class CrowdPanicGameTest implements FabricClientGameTest {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static <T extends Mob> T spawn(ServerLevel level, EntityType<T> type, double x, double z) {
        T mob = type.create(level, EntitySpawnReason.COMMAND);
        if (mob == null) throw new AssertionError("Could not create mob");
        mob.setPos(x, 101, z);
        level.addFreshEntity(mob);
        return mob;
    }

    @Override public void runTest(ClientGameTestContext context) {
        Mob[] crowd = new Mob[3];
        try (var world = context.worldBuilder().create()) {
            var server = world.getServer();
            server.runOnServer(s -> {
                var level = s.overworld();
                for (var pos : BlockPos.betweenClosed(-25, 100, -25, 30, 103, 25))
                    level.setBlockAndUpdate(pos, pos.getY() == 100 ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
                var player = s.getPlayerList().getPlayers().getFirst();
                var hit = level.damageSources().playerAttack(player);
                var sheep = spawn(level, EntityTypes.SHEEP, 0, 0);
                crowd[0] = spawn(level, EntityTypes.SHEEP, 4, 0);
                var far = spawn(level, EntityTypes.SHEEP, 17, 0);
                var other = spawn(level, EntityTypes.COW, 2, 0);
                sheep.hurtServer(level, hit, 1);
                check(crowd[0].getLastDamageSource() == hit, "Nearby sheep receives the hit stimulus");
                check(crowd[0].getHealth() == crowd[0].getMaxHealth(), "Signal causes no damage");
                check(crowd[0].hurtTime == 0, "Signal causes no hurt animation");
                check(far.getLastDamageSource() == null, "Radius is bounded and recipients do not relay");
                check(other.getLastDamageSource() == null, "Other species is unaffected");
                sheep.damageCooldownTime = 0;
                sheep.hurtServer(level, level.damageSources().fall(), 1);
                check(crowd[0].getLastDamageSource() == hit, "Environmental damage does not spread");
                var cow = spawn(level, EntityTypes.COW, 0, 10);
                crowd[1] = spawn(level, EntityTypes.COW, 4, 10);
                cow.hurtServer(level, hit, 1);
                var villager = spawn(level, EntityTypes.VILLAGER, 0, -10);
                crowd[2] = spawn(level, EntityTypes.VILLAGER, 4, -10);
                villager.hurtServer(level, hit, 1);
                var zombie = spawn(level, EntityTypes.ZOMBIE, 22, 10);
                var otherZombie = spawn(level, EntityTypes.ZOMBIE, 24, 10);
                zombie.hurtServer(level, hit, 1);
                check(otherZombie.getLastDamageSource() == null, "Hostile mobs do not signal");
                zombie.discard();
                otherZombie.discard();
                var protectedMob = spawn(level, EntityTypes.PIG, -20, 10);
                var protectedNeighbor = spawn(level, EntityTypes.PIG, -18, 10);
                protectedMob.hurtServer(level, level.damageSources().fall(), 1);
                check(!protectedMob.hurtServer(level, hit, 1), "Damage cooldown rejects equal hit");
                check(protectedNeighbor.getLastDamageSource() == null, "Rejected hits do not signal");
            });
            context.waitTicks(25);
            server.runOnServer(s -> {
                check(!crowd[0].getNavigation().isDone(), "Sheep crowd actually flees");
                check(!crowd[1].getNavigation().isDone(), "Cow crowd actually flees");
                check(VillagerPanicTrigger.isHurt(crowd[2]), "Villager sensor retains the shared damage source");
                check(crowd[2].getBrain().isActive(Activity.PANIC), "Villager crowd enters panic activity");
                for (Mob mob : crowd) check(mob.getHealth() == mob.getMaxHealth(), "Panicking neighbors remain unharmed");
            });
            context.waitTicks(30);
            server.runOnServer(s -> {
                for (Mob mob : crowd) check(mob.getLastDamageSource() == null, "Shared stimulus expires on vanilla timeout");
            });
        }
    }
}
