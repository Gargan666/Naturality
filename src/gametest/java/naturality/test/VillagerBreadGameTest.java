package naturality.test;

import naturality.villager.VillagerBreadState;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.util.ProblemReporter;

public final class VillagerBreadGameTest implements FabricClientGameTest {
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static Villager villager(ServerLevel level, net.minecraft.resources.ResourceKey<VillagerProfession> profession, double x) {
        var body = new Villager(EntityTypes.VILLAGER, level);
        body.setVillagerData(body.getVillagerData().withProfession(level.registryAccess(), profession));
        body.setVillagerXp(1);
        body.setPos(x, 101, .5);
        body.setOnGround(true);
        body.refreshBrain(level);
        body.getBrain().setActiveActivityIfPossible(Activity.IDLE);
        return body;
    }
    @Override public void runTest(ClientGameTestContext context) {
        try (var world = context.worldBuilder().create()) {
            var server = world.getServer();
            server.runCommand("time set 3000");
            server.runCommand("gamerule minecraft:advance_time false");
            server.runCommand("tp @a 0 106 -8");
            Villager[] farmer = new Villager[1];
            server.runOnServer(s -> {
                var level = s.overworld();
                for (var pos : BlockPos.betweenClosed(-16, 100, -16, 16, 104, 16))
                    level.setBlockAndUpdate(pos, pos.getY() == 100 ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
                var job = new BlockPos(0, 101, 2);
                level.setBlockAndUpdate(job, Blocks.COMPOSTER.defaultBlockState());
                farmer[0] = villager(level, VillagerProfession.FARMER, .5);
                farmer[0].setNoAi(true);
                farmer[0].getBrain().setMemory(MemoryModuleType.JOB_SITE, GlobalPos.of(level.dimension(), job));
                farmer[0].getBrain().setActiveActivityIfPossible(Activity.WORK);
                farmer[0].getInventory().addItem(new ItemStack(Items.BREAD, 4));
                level.addFreshEntity(farmer[0]);
                var routine = ((VillagerBreadState)farmer[0]).naturality$bread();
                routine.tick(level, farmer[0], level.getGameTime());
                check(routine.workedDay() == 0, "Farmer records its working day");
                for (int i = 0; i < 2000; i++) routine.tick(level, farmer[0], i);
                check(farmer[0].getInventory().countItem(Items.BREAD) == 4, "Farmer does not eat during its shift");
            });
            server.runCommand("time set 9000");
            server.runOnServer(s -> {
                var level = s.overworld();
                var body = farmer[0];
                body.getBrain().setActiveActivityIfPossible(Activity.IDLE);
                var routine = ((VillagerBreadState)body).naturality$bread();
                for (int i = 2000; i < 6000; i++) routine.tick(level, body, i);
                check(routine.eatenDay() == 0 && body.getInventory().countItem(Items.BREAD) == 3, "Farmer eats exactly one bread after its shift");
                var saved = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
                body.saveWithoutId(saved);
                var restored = new Villager(EntityTypes.VILLAGER, level);
                restored.load(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), saved.buildResult()));
                check(((VillagerBreadState)restored).naturality$bread().eatenDay() == 0, "Daily meal limit survives reload");
                check(restored.getInventory().countItem(Items.BREAD) == 3 && restored.getMainHandItem().isEmpty(), "Meal display does not duplicate bread on reload");
                for (var profession : java.util.List.of(VillagerProfession.NONE, VillagerProfession.NITWIT, VillagerProfession.BUTCHER,
                        VillagerProfession.FARMER, VillagerProfession.FISHERMAN, VillagerProfession.LIBRARIAN)) {
                    var receiver = villager(level, profession, 3);
                    check(receiver.wantsToPickUp(level, new ItemStack(Items.BREAD)), "Any profession accepts bread: " + profession);
                }
                var eater = villager(level, VillagerProfession.NITWIT, 3);
                eater.getInventory().addItem(new ItemStack(Items.BREAD, 4));
                var meals = ((VillagerBreadState)eater).naturality$bread();
                for (int i = 0; i < 4000; i++) meals.tick(level, eater, i);
                check(eater.getInventory().countItem(Items.BREAD) < 4, "Non-farmers randomly eat their bread");
                body.discard();
                body = restored; farmer[0] = body;
                level.addFreshEntity(body);
                body.getBrain().setActiveActivityIfPossible(Activity.IDLE);
                body.getInventory().removeItemType(Items.BREAD, 3);
                body.getInventory().addItem(new ItemStack(Items.WHEAT, 12));
                body.setNoAi(false);
                var receiver = villager(level, VillagerProfession.NITWIT, 6);
                level.addFreshEntity(receiver);
                receiver.setCustomName(net.minecraft.network.chat.Component.literal("Bread recipient"));
            });
            boolean[] pickedUp = {false}, tossed = {false};
            double[] furthest = {0};
            for (int i = 0; i < 1000 && !pickedUp[0]; i += 5) {
                context.waitTicks(5);
                server.runOnServer(s -> {
                    var level = s.overworld();
                    furthest[0] = Math.max(furthest[0], farmer[0].position().distanceToSqr(new net.minecraft.world.phys.Vec3(.5, 101, .5)));
                    tossed[0] |= !level.getEntitiesOfClass(ItemEntity.class, farmer[0].getBoundingBox().inflate(32), item -> item.getItem().is(Items.BREAD)).isEmpty();
                    pickedUp[0] = level.getEntitiesOfClass(Villager.class, farmer[0].getBoundingBox().inflate(32), body -> body != farmer[0])
                        .stream().anyMatch(body -> body.getInventory().countItem(Items.BREAD) > 0);
                });
            }
            server.runOnServer(s -> {
                check(pickedUp[0] && tossed[0], "Farmer tosses real bread that another villager picks up");
                check(furthest[0] > 1, "Farmer walks to villagers after work");
                check(farmer[0].getInventory().countItem(Items.WHEAT) < 12, "Shared bread is made from harvested wheat");
            });
            context.takeScreenshot("farmer-evening-bread");
            server.runCommand("time set 27000");
            server.runOnServer(s -> {
                var body = farmer[0]; body.setNoAi(true);
                body.getBrain().setActiveActivityIfPossible(Activity.WORK);
                ((VillagerBreadState)body).naturality$bread().tick(s.overworld(), body, 6000);
            });
            server.runCommand("time set 37000");
            server.runOnServer(s -> {
                var body = farmer[0];
                body.getBrain().setActiveActivityIfPossible(Activity.IDLE);
                body.getInventory().clearContent(); body.getInventory().addItem(new ItemStack(Items.BREAD, 4));
                var routine = ((VillagerBreadState)body).naturality$bread();
                for (int i = 6001; i < 10000; i++) routine.tick(s.overworld(), body, i);
                check(routine.eatenDay() == 1 && body.getInventory().countItem(Items.BREAD) == 3, "Farmer can eat once again after the next shift");
            });
        }
    }
}
