package naturality.test;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.npc.villager.*;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

public final class ButcherWorkGameTest implements FabricClientGameTest {
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static Villager worker(ServerLevel level, BlockPos job, net.minecraft.resources.ResourceKey<VillagerProfession> profession) {
        var body = new Villager(EntityTypes.VILLAGER, level);
        body.setPos(job.getX() + .5, 101, job.getZ() - 1.5);
        body.setVillagerData(body.getVillagerData().withProfession(level.registryAccess(), profession));
        body.setVillagerXp(1);
        body.getBrain().setMemory(MemoryModuleType.JOB_SITE, GlobalPos.of(level.dimension(), job));
        body.refreshBrain(level);
        level.addFreshEntity(body);
        return body;
    }
    @Override public void runTest(ClientGameTestContext context) {
        Villager[] butcher = new Villager[1], farmer = new Villager[1];
        Animal[] parents = new Animal[2], protectedBaby = new Animal[1];
        boolean[] born = {false}, finished = {false};
        boolean[] swordUsed = {false};
        long[] birthTime = {-1};
        try (var world = context.worldBuilder().create()) {
            var server = world.getServer();
            server.runCommand("time set 3000");
            server.runCommand("gamerule minecraft:advance_time false");
            server.runCommand("tp @a 3 104 -10 facing 3 101 1");
            server.runOnServer(s -> {
                var level = s.overworld();
                for (var pos : BlockPos.betweenClosed(-18, 100, -18, 24, 104, 18))
                    level.setBlockAndUpdate(pos, pos.getY() == 100 ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
                var smoker = new BlockPos(0, 101, 0);
                var compost = new BlockPos(10, 101, 0);
                level.setBlockAndUpdate(smoker, Blocks.SMOKER.defaultBlockState());
                level.setBlockAndUpdate(compost, Blocks.COMPOSTER.defaultBlockState());
                butcher[0] = worker(level, smoker, VillagerProfession.BUTCHER);
                farmer[0] = worker(level, compost, VillagerProfession.FARMER);
                for (var food : new net.minecraft.world.item.Item[]{Items.WHEAT, Items.CARROT, Items.WHEAT_SEEDS})
                    check(butcher[0].getInventory().countItem(food) == 4, "Butcher starts with four of each breeding supply");
                var saved = net.minecraft.world.level.storage.TagValueOutput.createWithContext(
                    net.minecraft.util.ProblemReporter.DISCARDING, level.registryAccess());
                butcher[0].saveWithoutId(saved);
                var restored = new Villager(EntityTypes.VILLAGER, level);
                restored.load(net.minecraft.world.level.storage.TagValueInput.create(
                    net.minecraft.util.ProblemReporter.DISCARDING, level.registryAccess(), saved.buildResult()));
                check(restored.getInventory().countItem(Items.WHEAT) == 4,
                    "Reloading a butcher does not duplicate starter supplies");
                for (int i = 0; i < 2; i++) {
                    parents[i] = EntityTypes.COW.create(level, EntitySpawnReason.COMMAND);
                    parents[i].setPos(3 + i * 2, 101, 1);
                    level.addFreshEntity(parents[i]);
                }
                protectedBaby[0] = EntityTypes.COW.create(level, EntitySpawnReason.COMMAND);
                protectedBaby[0].setBaby(true);
                protectedBaby[0].setPos(8, 101, 4);
                level.addFreshEntity(protectedBaby[0]);
            });
            for (int tick = 0; tick < 1000 && !finished[0]; tick += 10) {
                context.waitTicks(10);
                server.runOnServer(s -> {
                    var level = s.overworld();
                    swordUsed[0] |= butcher[0].getMainHandItem().is(Items.IRON_SWORD);
                    var children = level.getEntitiesOfClass(Animal.class, butcher[0].getBoundingBox().inflate(24),
                        a -> a.isBaby() && a != protectedBaby[0]);
                    if (!born[0] && !children.isEmpty()) {
                        born[0] = true; birthTime[0] = level.getGameTime();
                        check(parents[0].getHealth() == parents[0].getMaxHealth()
                            && parents[1].getHealth() == parents[1].getMaxHealth(), "Parents are not attacked before their child is born");
                    }
                    if (born[0] && level.getGameTime() - birthTime[0] < 10)
                        check(parents[0].isAlive() && parents[1].isAlive(), "Butcher waits before harvesting parents");
                    check(protectedBaby[0].isAlive(), "Butcher never attacks an existing baby");
                    finished[0] = born[0] && (!parents[0].isAlive() ^ !parents[1].isAlive())
                        && butcher[0].getInventory().countItem(Items.BEEF) > 0;
                });
            }
            server.runOnServer(s -> {
                check(finished[0], "Butcher breeds cows, harvests one parent, and collects their drops");
                check(swordUsed[0], "Butcher equips an iron sword when harvesting parents");
                check(parents[0].isAlive() ^ parents[1].isAlive(), "Butcher leaves one cow parent alive");
                check(butcher[0].getInventory().countItem(Items.WHEAT) == 2, "Breeding consumes one wheat per parent");
            });
            context.takeScreenshot("butcher-harvested-parents");
            Animal[] chickens = new Animal[2];
            server.runOnServer(s -> {
                var level = s.overworld();
                butcher[0].getInventory().removeItemType(Items.WHEAT, 2);
                for (int i = 0; i < 2; i++) {
                    chickens[i] = EntityTypes.CHICKEN.create(level, EntitySpawnReason.COMMAND);
                    chickens[i].setPos(3 + i, 101, 1);
                    level.addFreshEntity(chickens[i]);
                }
            });
            boolean[] switched = {false};
            for (int tick = 0; tick < 600 && !switched[0]; tick += 10) {
                context.waitTicks(10);
                server.runOnServer(s -> switched[0] = (!chickens[0].isAlive() ^ !chickens[1].isAlive())
                    && butcher[0].getInventory().countItem(Items.CHICKEN) > 0);
            }
            server.runOnServer(s -> {
                check(switched[0], "Butcher switches to chickens when wheat runs out and collects chicken drops: "
                    + butcher[0].position() + " inventory=" + butcher[0].getInventory()
                    + " chicken0pos=" + chickens[0].position() + " food=" + chickens[0].isFood(new net.minecraft.world.item.ItemStack(Items.WHEAT_SEEDS)) + " path=" + butcher[0].getNavigation().createPath(chickens[0], 2) + " chicken0=" + chickens[0].getHealth() + "/" + chickens[0].getAge() + "/" + chickens[0].isInLove()
                    + " chicken1pos=" + chickens[1].position() + " chicken1=" + chickens[1].getHealth() + "/" + chickens[1].getAge() + "/" + chickens[1].isInLove()
                    + " work=" + butcher[0].getBrain().isActive(net.minecraft.world.entity.schedule.Activity.WORK)
                    + " worldItems=" + s.overworld().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, butcher[0].getBoundingBox().inflate(40)).stream().map(item -> item.getItem() + "@" + item.position()).toList() + " behaviors=" + butcher[0].getBrain().getRunningBehaviors().stream().map(b -> b.debugString()).toList());
                check(chickens[0].isAlive() ^ chickens[1].isAlive(), "Butcher leaves one chicken parent alive");
                check(butcher[0].getInventory().countItem(Items.WHEAT_SEEDS) == 2,
                    "The new animal pair consumes two seeds");
                check(butcher[0].getInventory().countItem(Items.CARROT) == 4,
                    "Unavailable animal types do not consume their supplies");
                butcher[0].getInventory().removeItemType(Items.WHEAT_SEEDS, 2);
            });
            context.waitTicks(160);
            server.runOnServer(s -> {
                check(butcher[0].getInventory().countItem(Items.WHEAT_SEEDS) == 0, "No adult pair means no supply trip");
                check(butcher[0].getInventory().countItem(Items.CARROT) == 4, "Unneeded supplies remain unchanged while waiting");
                for (Animal chicken : chickens) if (chicken.isAlive()) chicken.discard();
                for (int i = 0; i < 2; i++) {
                    chickens[i] = EntityTypes.CHICKEN.create(s.overworld(), EntitySpawnReason.COMMAND);
                    chickens[i].setPos(3 + i, 101, 1); s.overworld().addFreshEntity(chickens[i]);
                }
            });
            boolean[] restocked = {false};
            for (int tick = 0; tick < 500 && !restocked[0]; tick += 10) {
                context.waitTicks(10);
                server.runOnServer(s -> restocked[0] = butcher[0].getInventory().countItem(Items.WHEAT) == 8
                    && butcher[0].getInventory().countItem(Items.CARROT) == 12
                    && butcher[0].getInventory().countItem(Items.WHEAT_SEEDS) == 8);
            }
            server.runOnServer(s -> {
                check(restocked[0], "Missing selected food triggers eight of each supply even with carrots remaining");
                for (Animal chicken : chickens) chicken.discard();
            });
            context.takeScreenshot("butcher-farmer-restock");
            context.waitTicks(100);
            server.runOnServer(s -> {
                check(new BlockPos(0, 101, 0).closerToCenterThan(butcher[0].position(), 2.5),
                    "Restocked butcher returns to its workstation");
                check(naturality.villager.FarmerSupplies.display(farmer[0]).isEmpty(),
                    "Farmer's delivery interruption ends after restocking");
                check(!butcher[0].getMainHandItem().is(Items.IRON_SWORD),
                    "Butcher puts away its sword outside harvesting");
                for (Animal animal : s.overworld().getEntitiesOfClass(Animal.class, butcher[0].getBoundingBox().inflate(40))) animal.discard();
            });
            for (var type : java.util.List.of(EntityTypes.PIG, EntityTypes.SHEEP)) {
                Animal[] pair = new Animal[2];
                var meat = type == EntityTypes.PIG ? Items.PORKCHOP : Items.MUTTON;
                var food = type == EntityTypes.PIG ? Items.CARROT : Items.WHEAT;
                int[] previousFood = {0};
                server.runOnServer(s -> {
                    // Make room for each species' drops in the eight-slot villager inventory.
                    for (var drop : java.util.List.of(Items.BEEF, Items.LEATHER, Items.CHICKEN, Items.FEATHER, Items.PORKCHOP, Items.MUTTON))
                        butcher[0].getInventory().removeItemType(drop, 64);
                    previousFood[0] = butcher[0].getInventory().countItem(food);
                    for (int i = 0; i < 2; i++) {
                        pair[i] = type.create(s.overworld(), EntitySpawnReason.COMMAND);
                        pair[i].setPos(3 + i, 101, 1); s.overworld().addFreshEntity(pair[i]);
                    }
                });
                boolean[] harvested = {false};
                for (int tick = 0; tick < 1000 && !harvested[0]; tick += 10) {
                    context.waitTicks(10);
                    server.runOnServer(s -> harvested[0] = (pair[0].isAlive() ^ pair[1].isAlive())
                        && butcher[0].getInventory().countItem(meat) > 0);
                }
                server.runOnServer(s -> {
                    check(harvested[0], "Butcher breeds and harvests " + type + " inventory=" + butcher[0].getInventory()
                        + " body=" + butcher[0].position() + " pair=" + pair[0].getHealth() + "/" + pair[0].getAge() + "@" + pair[0].position()
                        + "," + pair[1].getHealth() + "/" + pair[1].getAge() + "@" + pair[1].position()
                        + " behavior=" + butcher[0].getBrain().getRunningBehaviors().stream().map(b -> b.debugString()).toList());
                    check(butcher[0].getInventory().countItem(food) == previousFood[0] - 2, "Each parent consumes its correct breeding food");
                    check(s.overworld().getEntitiesOfClass(Animal.class, butcher[0].getBoundingBox().inflate(40),
                        animal -> animal.getType() == type && animal.isBaby()).size() == 1, "Baby survives harvesting");
                    for (Animal animal : s.overworld().getEntitiesOfClass(Animal.class, butcher[0].getBoundingBox().inflate(40))) animal.discard();
                });
                context.waitTicks(100);
            }
            Animal[] nearby = new Animal[4];
            server.runOnServer(s -> {
                butcher[0].setPos(.5, 101, -1.5);
                for (int i = 0; i < nearby.length; i++) {
                    nearby[i] = (i < 2 ? EntityTypes.CHICKEN : EntityTypes.COW).create(s.overworld(), EntitySpawnReason.COMMAND);
                    nearby[i].setPos(i < 2 ? 3 + i : 12 + i, 101, 1);
                    nearby[i].setNoAi(true);
                    s.overworld().addFreshEntity(nearby[i]);
                }
            });
            boolean[] selected = {false};
            for (int tick = 0; tick < 100 && !selected[0]; tick++) {
                context.waitTicks(1);
                server.runOnServer(s -> selected[0] = butcher[0].getMainHandItem().is(Items.WHEAT_SEEDS));
            }
            server.runOnServer(s -> {
                check(selected[0], "Nearby chickens take priority over distant cows despite previous wheat selection");
                for (Animal animal : nearby) animal.discard();
                butcher[0].setNoAi(true);
                butcher[0].setPos(.5, 101, -1.5);
            });
            for (var type : java.util.List.of(EntityTypes.COW, EntityTypes.SHEEP, EntityTypes.CHICKEN, EntityTypes.PIG)) {
                Animal[] attracted = new Animal[1];
                var lure = type == EntityTypes.PIG ? Items.CARROT : type == EntityTypes.CHICKEN ? Items.WHEAT_SEEDS : Items.WHEAT;
                server.runOnServer(s -> {
                    butcher[0].setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new net.minecraft.world.item.ItemStack(lure));
                    attracted[0] = type.create(s.overworld(), EntitySpawnReason.COMMAND);
                    attracted[0].setPos(8.5, 101, -1.5);
                    s.overworld().addFreshEntity(attracted[0]);
                });
                boolean[] followed = {false};
                for (int tick = 0; tick < 160 && !followed[0]; tick += 5) {
                    context.waitTicks(5);
                    server.runOnServer(s -> followed[0] = attracted[0].distanceToSqr(butcher[0]) < 12);
                }
                server.runOnServer(s -> {
                    check(followed[0], "Held breeding food attracts " + type + " to a stationary butcher");
                    attracted[0].discard();
                });
            }
        }
    }
}
