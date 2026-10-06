package naturality.test;

import naturality.villager.AnimalCareWork;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.npc.villager.*;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.*;

public final class AnimalCareGameTest implements FabricClientGameTest {
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static Villager worker(ServerLevel level, net.minecraft.resources.ResourceKey<VillagerProfession> profession, double x) {
        var body = new Villager(EntityTypes.VILLAGER, level);
        body.setPos(x, 101, .5); body.setOnGround(true); body.setVillagerXp(1);
        body.setVillagerData(body.getVillagerData().withProfession(level.registryAccess(), profession));
        body.getBrain().setMemory(MemoryModuleType.JOB_SITE, GlobalPos.of(level.dimension(), new BlockPos((int)x, 101, -3)));
        body.refreshBrain(level); body.getBrain().setActiveActivityIfPossible(Activity.WORK);
        level.addFreshEntity(body);
        return body;
    }
    @Override public void runTest(ClientGameTestContext context) {
        try (var world = context.worldBuilder().create()) {
            var server = world.getServer();
            server.runCommand("gamemode spectator @a");
            server.runCommand("tp @a 0 105 -4");
            server.runCommand("time set 3000");
            server.runCommand("gamerule minecraft:advance_time false");
            server.runCommand("fill -16 100 -16 16 105 16 air");
            server.runCommand("fill -16 100 -16 16 100 16 stone");
            Villager[] shepherd = new Villager[1];
            Sheep[] sheep = new Sheep[2];
            long[] firstShear = {-1}; boolean[] finished = {false}, held = {false};
            server.runOnServer(s -> {
                var level = s.overworld();
                level.setBlockAndUpdate(new BlockPos(0, 101, -3), net.minecraft.world.level.block.Blocks.LOOM.defaultBlockState());
                shepherd[0] = worker(level, VillagerProfession.SHEPHERD, .5);
                for (int i = 0; i < 2; i++) {
                    sheep[i] = EntityTypes.SHEEP.create(level, EntitySpawnReason.COMMAND);
                    sheep[i].setPos(2 + i * 4, 101, .5); sheep[i].setNoAi(true);
                    level.addFreshEntity(sheep[i]);
                }
            });
            for (int tick = 0; tick < 420 && !finished[0]; tick += 2) {
                context.waitTicks(2);
                server.runOnServer(s -> {
                    held[0] |= shepherd[0].getMainHandItem().is(Items.SHEARS);
                    if (firstShear[0] < 0 && sheep[0].isSheared()) firstShear[0] = s.overworld().getGameTime();
                    check(!sheep[1].isSheared() || sheep[0].isSheared(), "Nearest sheep is sheared first");
                    if (firstShear[0] >= 0 && s.overworld().getGameTime() - firstShear[0] < 98)
                        check(!sheep[1].isSheared(), "Five-second shearing cooldown");
                    finished[0] = sheep[0].isSheared() && sheep[1].isSheared()
                        && shepherd[0].getInventory().countItem(Items.WOOL.pick(sheep[0].getColor())) >= 2;
                });
            }
            server.runOnServer(s -> {
                check(finished[0] && held[0], "Shepherd walks, displays shears, shears both sheep and collects wool");
                shepherd[0].discard(); for (var animal : sheep) animal.discard();
                var level = s.overworld();
                var toolsmith = worker(level, VillagerProfession.TOOLSMITH, .5);
                var weaponsmith = worker(level, VillagerProfession.WEAPONSMITH, 3.5);
                toolsmith.setNoAi(true); weaponsmith.setNoAi(true);
                var golem = EntityTypes.IRON_GOLEM.create(level, EntitySpawnReason.COMMAND);
                golem.setPos(1.5, 101, .5); golem.setNoAi(true); golem.setHealth(20); level.addFreshEntity(golem);
                var first = new AnimalCareWork(false); var second = new AnimalCareWork(false);
                check(first.tryStart(level, toolsmith, 1000) && second.tryStart(level, weaponsmith, 1000), "Both smith professions can repair");
                second.tickOrStop(level, weaponsmith, 1000);
                first.tickOrStop(level, toolsmith, 1000);
                check(toolsmith.getMainHandItem().is(Items.IRON_INGOT) && weaponsmith.getMainHandItem().isEmpty(), "Only nearest smith takes the repair assignment");
                first.tickOrStop(level, toolsmith, 1020); second.tickOrStop(level, weaponsmith, 1020);
                check(golem.getHealth() == 45, "One ingot repairs vanilla 25 health");
                for (long time = 1021; time <= 1080; time++) {
                    second.tickOrStop(level, weaponsmith, time); first.tickOrStop(level, toolsmith, time);
                }
                check(golem.getHealth() == golem.getMaxHealth(), "Assigned smith repeats repairs to full health");
                check(toolsmith.getMainHandItem().isEmpty(), "Smith restores its hand when finished");
                toolsmith.setPos(10, 101, .5); golem.setHealth(75);
                second.tickOrStop(level, weaponsmith, 1120);
                check(weaponsmith.getMainHandItem().is(Items.IRON_INGOT), "Weaponsmith can take the next assignment");
                second.tickOrStop(level, weaponsmith, 1140);
                check(golem.getHealth() == golem.getMaxHealth(), "Weaponsmith repairs the golem");
                golem.setHealth(50); toolsmith.setPos(.5, 101, .5);
                weaponsmith.getBrain().setActiveActivityIfPossible(Activity.IDLE);
                first.tickOrStop(level, toolsmith, 1160);
                check(toolsmith.getMainHandItem().is(Items.IRON_INGOT), "Smith starts another repair before interruption");
                toolsmith.getBrain().setActiveActivityIfPossible(Activity.IDLE);
                first.tickOrStop(level, toolsmith, 1180);
                check(toolsmith.getMainHandItem().isEmpty(), "Off-duty interruption clears displayed equipment");
                toolsmith.discard(); weaponsmith.discard(); golem.discard();
            });
            Villager[] walkingSmith = new Villager[1]; IronGolem[] damaged = new IronGolem[1];
            boolean[] walked = {false}, repaired = {false}, ingot = {false};
            server.runOnServer(s -> {
                var level = s.overworld();
                level.setBlockAndUpdate(new BlockPos(0, 101, -3), net.minecraft.world.level.block.Blocks.BLAST_FURNACE.defaultBlockState());
                walkingSmith[0] = worker(level, VillagerProfession.ARMORER, .5);
                damaged[0] = EntityTypes.IRON_GOLEM.create(level, EntitySpawnReason.COMMAND);
                damaged[0].setPos(8, 101, .5); damaged[0].setNoAi(true); damaged[0].setHealth(10);
                level.addFreshEntity(damaged[0]);
            });
            for (int tick = 0; tick < 300 && !repaired[0]; tick += 2) {
                context.waitTicks(2);
                server.runOnServer(s -> {
                    walked[0] |= walkingSmith[0].getX() > 3;
                    ingot[0] |= walkingSmith[0].getMainHandItem().is(Items.IRON_INGOT);
                    repaired[0] = damaged[0].getHealth() == damaged[0].getMaxHealth();
                });
            }
            server.runOnServer(s -> {
                check(walked[0] && ingot[0] && repaired[0], "Live armorer walks to damaged golem, holds ingots and repairs fully");
                walkingSmith[0].discard(); damaged[0].discard();
            });
        }
    }
}
