package naturality.test;

import naturality.villager.ProfessionWork;
import naturality.villager.VillagerWorkVisuals;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ComposterBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmlandBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;

public final class VillagerWorkGameTest implements FabricClientGameTest {
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    @Override public void runTest(ClientGameTestContext context) {
        Villager[] actors = new Villager[2];
        CompostFixture[] compostFixture = new CompostFixture[1];
        var crop = new BlockPos(3, 101, 0);
        var compost = new BlockPos(0, 101, 1);
        try (var world = context.worldBuilder().create()) {
            var server = world.getServer();
            server.runCommand("time set 3000");
            server.runCommand("gamerule minecraft:advance_time false");
            server.runCommand("gamerule minecraft:random_tick_speed 0");
            server.runCommand("tp @a 0 105 -8");
            server.runOnServer(s -> {
                var level = s.overworld();
                for (var p : BlockPos.betweenClosed(-20, 100, -12, 24, 104, 12))
                    level.setBlockAndUpdate(p, p.getY() == 100 ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
                level.setBlockAndUpdate(compost, Blocks.COMPOSTER.defaultBlockState().setValue(ComposterBlock.LEVEL, 8));
                checkBatchFarming(level);
                checkEvenFertilizing(level);
                compostFixture[0] = beginComposting(level);
                level.setBlockAndUpdate(crop.below(), Blocks.FARMLAND.defaultBlockState().setValue(FarmlandBlock.MOISTURE, 7));
                level.setBlockAndUpdate(crop, ((CropBlock)Blocks.WHEAT).getStateForAge(7));
                actors[0] = worker(level, compost, true);
                checkStationPath(actors[0], compost);
                actors[0].getInventory().addItem(new ItemStack(Items.WHEAT_SEEDS, 32));
                check(actors[0].getInventory().countItem(Items.BONE_MEAL) == 15, "Village farmer receives 15 bone meal");
                var saved = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
                actors[0].saveWithoutId(saved);
                var restored = new Villager(EntityTypes.VILLAGER, level);
                restored.load(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), saved.buildResult()));
                check(restored.getInventory().countItem(Items.BONE_MEAL) == 15, "Save/load must not refill supplies");
                restored.getInventory().removeItemType(Items.BONE_MEAL, 15);
                restored.setVillagerData(restored.getVillagerData().withProfession(level.registryAccess(), VillagerProfession.FISHERMAN));
                restored.setVillagerData(restored.getVillagerData().withProfession(level.registryAccess(), VillagerProfession.FARMER));
                check(restored.getInventory().countItem(Items.BONE_MEAL) == 0, "Profession cycling must not refill supplies");
                var pending = new Villager(EntityTypes.VILLAGER, level);
                pending.finalizeSpawn(level, level.getCurrentDifficultyAt(compost), EntitySpawnReason.STRUCTURE, null);
                var pendingSave = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
                pending.saveWithoutId(pendingSave);
                var laterFarmer = new Villager(EntityTypes.VILLAGER, level);
                laterFarmer.load(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), pendingSave.buildResult()));
                laterFarmer.setVillagerData(laterFarmer.getVillagerData().withProfession(level.registryAccess(), VillagerProfession.FARMER));
                check(laterFarmer.getInventory().countItem(Items.BONE_MEAL) == 15, "Village recruits retain pending supplies through save/load");
                var summoned = new Villager(EntityTypes.VILLAGER, level);
                summoned.setVillagerData(summoned.getVillagerData().withProfession(level.registryAccess(), VillagerProfession.FARMER));
                summoned.finalizeSpawn(level, level.getCurrentDifficultyAt(compost), EntitySpawnReason.COMMAND, null);
                check(summoned.getInventory().countItem(Items.BONE_MEAL) == 0, "Only village structure spawns receive starting supplies");

                var barrel = new BlockPos(12, 101, 1);
                level.setBlockAndUpdate(barrel, Blocks.BARREL.defaultBlockState());
                for (var p : BlockPos.betweenClosed(15, 99, -2, 20, 99, 3)) level.setBlockAndUpdate(p, Blocks.STONE.defaultBlockState());
                for (var p : BlockPos.betweenClosed(15, 100, -2, 20, 100, 3)) level.setBlockAndUpdate(p, Blocks.WATER.defaultBlockState());
                check(ProfessionWork.restingWater(level, new BlockPos(15, 100, 0)), "Still shoreline qualifies");
                var falling = new BlockPos(10, 102, 5);
                level.setBlockAndUpdate(falling, Blocks.WATER.defaultBlockState().setValue(LiquidBlock.LEVEL, 8));
                check(!ProfessionWork.restingWater(level, falling), "Falling waterfall water must be rejected");
                check(!ProfessionWork.restingWater(level, new BlockPos(1000000, 100, 1000000)), "Absent chunks must be ignored");
                FishingSpotChecks.run(level, barrel);
                FishermanCastingChecks.run(level, barrel);
                actors[1] = worker(level, barrel, false);
                checkStationPath(actors[1], barrel);
                net.minecraft.world.phys.Vec3 previousLaunch = null;
                for (int castIndex = 0; castIndex < 8; castIndex++) {
                    var castHook = new naturality.villager.VillagerBobber(level, actors[1], new BlockPos(18, 100, 0));
                    var launch = castHook.getDeltaMovement();
                    double pitch = Math.toDegrees(Math.atan2(launch.y, Math.hypot(launch.x, launch.z)));
                    check(pitch >= 15 && pitch <= 50, "Fisherman casts with a randomized upward angle");
                    check(previousLaunch == null || launch.distanceToSqr(previousLaunch) > 1e-8, "Consecutive casts use different launch vectors");
                    var towardWater = net.minecraft.world.phys.Vec3.atCenterOf(new BlockPos(18, 100, 0))
                        .subtract(castHook.position()).multiply(1, 0, 1).normalize();
                    check(launch.multiply(1, 0, 1).normalize().dot(towardWater) >= Math.cos(Math.toRadians(35)) - 1e-6,
                        "Random casts remain in the forward water-facing cone");
                    previousLaunch = launch;
                }
                actors[1].getOffers().clear();
                actors[1].getOffers().add(new MerchantOffer(new ItemCost(Items.EMERALD, 1), new ItemStack(Items.COOKED_COD, 6), 16, 1, .05F));
                actors[1].getOffers().add(new MerchantOffer(new ItemCost(Items.COD, 15), new ItemStack(Items.EMERALD), 16, 1, .05F));
                for (int slot = 0; slot < actors[1].getInventory().getContainerSize(); slot++)
                    actors[1].getInventory().setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
                for (int i = 0; i < 100; i++) {
                    int delay = ProfessionWork.catchDelay(actors[1]);
                    check(delay >= 200 && delay <= 300, "Fishing interval must stay in 10-15 seconds");
                }
            });
            context.waitTicks(30);
            server.runOnServer(s -> finishComposting(s.overworld(), compostFixture[0]));
            context.waitTicks(240);
            server.runOnServer(s -> {
                check(actors[0].getInventory().countItem(Items.WHEAT) > 0 || !s.overworld().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new net.minecraft.world.phys.AABB(crop).inflate(6), e -> e.getItem().is(Items.WHEAT)).isEmpty(), "Farmer seeks and harvests ripe crops");
                check(s.overworld().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new net.minecraft.world.phys.AABB(compost).inflate(4), e -> e.getItem().is(Items.BONE_MEAL)).isEmpty(), "Composter produce is collected directly rather than dropped");
                check(actors[0].getInventory().countItem(Items.WHEAT_SEEDS) < 32, "Farmer composts surplus seeds");
            });
            context.waitTicks(360);
            boolean[] activeCast = {false};
            for (int attempt = 0; attempt < 30 && !activeCast[0]; attempt++) {
                server.runOnServer(s -> {
                    var cast = ((VillagerWorkVisuals)actors[1]).naturality$castTarget();
                    activeCast[0] = cast != null && actors[1].getMainHandItem().is(Items.FISHING_ROD)
                        && !s.overworld().getEntitiesOfClass(naturality.villager.VillagerBobber.class,
                            new net.minecraft.world.phys.AABB(10, 95, -5, 25, 107, 8),
                            e -> e.owner() == actors[1] && e.phase() <= naturality.villager.VillagerBobber.BOBBING).isEmpty();
                    if (activeCast[0]) {
                        check(ProfessionWork.restingWater(s.overworld(), cast)
                            && cast.distSqr(actors[1].blockPosition()) >= 9,
                            "Fisherman casts several blocks into resting water");
                        int interior = 0;
                        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++)
                            if (ProfessionWork.restingWater(s.overworld(), cast.offset(dx, 0, dz))) interior++;
                        check(interior == 9, "Fisherman aims at the interior of the connected pool");
                    }
                });
                if (!activeCast[0]) context.waitTicks(10);
            }
            check(activeCast[0], "Fisherman resumes casting after its barrel visit");
            int fishermanId = actors[1].getId();
            boolean[] clientHook = {false};
            for (int attempt = 0; attempt < 30 && !clientHook[0]; attempt++) {
                context.waitTicks(2);
                context.runOnClient(client -> {
                    var entity = client.level.getEntity(fishermanId);
                    if (!(entity instanceof Villager villager)) return;
                    clientHook[0] = !client.level.getEntitiesOfClass(naturality.villager.VillagerBobber.class,
                        new net.minecraft.world.phys.AABB(15, 99, -2, 21, 103, 4), e -> e.owner() == villager).isEmpty()
                        && new net.minecraft.client.renderer.item.properties.conditional.FishingRodCast()
                            .get(villager.getMainHandItem(), client.level, villager, 0,
                                net.minecraft.world.item.ItemDisplayContext.THIRD_PERSON_RIGHT_HAND);
                });
            }
            check(clientHook[0], "Client receives a separate bobber and uses the vanilla cast rod model");
            server.runCommand("tp @a 19 103 -5 facing 18 101 0");
            context.waitTicks(2);
            context.takeScreenshot("villager-bobber-entity");
            server.runCommand("tp @a 16 102 -1 facing 16 101 0");
            context.waitTicks(2);
            context.takeScreenshot("villager-bobber-model-close");
            double[] poseViews = new double[12];
            server.runOnServer(s -> {
                var center = actors[1].position();
                var look = actors[1].getLookAngle();
                var forward = new net.minecraft.world.phys.Vec3(look.x, 0, look.z).normalize();
                var side = new net.minecraft.world.phys.Vec3(-forward.z, 0, forward.x);
                var sideCamera = center.add(side.scale(3));
                var frontCamera = center.add(forward.scale(3));
                poseViews[0] = sideCamera.x; poseViews[1] = sideCamera.y; poseViews[2] = sideCamera.z;
                poseViews[3] = frontCamera.x; poseViews[4] = frontCamera.y; poseViews[5] = frontCamera.z;
                poseViews[6] = center.x; poseViews[7] = center.y + .3; poseViews[8] = center.z;
            });
            server.runCommand(String.format(java.util.Locale.ROOT,
                "tp @a %.3f %.3f %.3f facing %.3f %.3f %.3f",
                poseViews[0], poseViews[1], poseViews[2], poseViews[6], poseViews[7], poseViews[8]));
            context.waitTicks(4);
            context.takeScreenshot("villager-rod-side-pose");
            server.runCommand(String.format(java.util.Locale.ROOT,
                "tp @a %.3f %.3f %.3f facing %.3f %.3f %.3f",
                poseViews[3], poseViews[4], poseViews[5], poseViews[6], poseViews[7], poseViews[8]));
            context.waitTicks(4);
            context.takeScreenshot("villager-rod-front-pose");
            server.runCommand("tp @a 17 103 -5 facing 17 101 1");
            context.waitTicks(2);
            context.takeScreenshot("villager-bobber-alternate-view");
            server.runCommand("tp @a 19 103 -5 facing 18 101 0");
            boolean[] replanted = {false};
            for (int attempt = 0; attempt < 20 && !replanted[0]; attempt++) {
                server.runOnServer(s -> replanted[0] = s.overworld().getBlockState(crop).getBlock() instanceof CropBlock);
                if (!replanted[0]) context.waitTicks(10);
            }
            server.runOnServer(s -> {
                check(actors[0].getInventory().countItem(Items.BONE_MEAL) < 16, "Farmer uses bone meal on growing crops");
                check(replanted[0], "Harvested plots are replanted after the intentional planting pause");
                check(actors[1].getInventory().countItem(Items.COOKED_COD) == 0,
                    "Fishing remains visual even with a full villager inventory");
                check(!actors[1].isInWater(), "Fisherman stands on dry bank");
            });
            boolean[] bite = {false};
            boolean[] readyForBite = {false};
            int[] beforeRetrieve = {0};
            ItemStack[] villagerCatch = new ItemStack[1];
            for (int attempt = 0; attempt < 120 && !bite[0]; attempt++) {
                server.runOnServer(s -> {
                    var hooks = s.overworld().getEntitiesOfClass(naturality.villager.VillagerBobber.class,
                        new net.minecraft.world.phys.AABB(15, 99, -2, 21, 103, 4),
                        e -> e.owner() == actors[1]);
                    readyForBite[0] |= hooks.stream().anyMatch(e -> e.phase() == naturality.villager.VillagerBobber.BOBBING);
                    bite[0] = readyForBite[0] && hooks.stream().anyMatch(e -> e.phase() == naturality.villager.VillagerBobber.BITING);
                    if (bite[0]) {
                        villagerCatch[0] = hooks.stream().filter(e -> e.phase() == naturality.villager.VillagerBobber.BITING)
                            .findFirst().orElseThrow().catchItem().copy();
                        check(!villagerCatch[0].isEmpty() && !villagerCatch[0].is(Items.COOKED_COD),
                            "Bite displays vanilla fishing loot rather than the cooked-cod trade result");
                        beforeRetrieve[0] = actors[1].getInventory().countItem(villagerCatch[0].getItem());
                    }
                });
                if (!bite[0]) context.waitTicks(5);
            }
            check(bite[0], "Fisherman bobber visibly bites before retrieval");
            context.waitTicks(10);
            server.runOnServer(s -> check(!s.overworld().getEntitiesOfClass(naturality.villager.VillagerBobber.class,
                new net.minecraft.world.phys.AABB(15, 99, -2, 21, 103, 4),
                e -> e.owner() == actors[1] && e.phase() == naturality.villager.VillagerBobber.BITING).isEmpty(),
                "Bite lasts about one second before reel-in"));
            context.takeScreenshot("villager-bobber-bite");
            context.waitTicks(11);
            context.takeScreenshot("villager-bobber-retrieve");
            context.waitTicks(20);
            server.runOnServer(s -> {
                check(actors[1].getInventory().countItem(villagerCatch[0].getItem()) == beforeRetrieve[0],
                    "Visual fishing catch never enters the villager inventory");
                check(s.overworld().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                    new net.minecraft.world.phys.AABB(15, 99, -2, 21, 103, 4),
                    e -> e.getItem().is(villagerCatch[0].getItem())).isEmpty(),
                    "Visual fishing catch does not overflow onto the ground");
                actors[1].setTradingPlayer(world.getConnection().getServerPlayer());
            });
            context.waitTicks(10);
            server.runOnServer(s -> {
                check(actors[1].getMainHandItem().isEmpty(), "Trading interrupts fishing and clears the rod");
                check(((VillagerWorkVisuals)actors[1]).naturality$castTarget() == null, "Trading reels in the fishing line");
                actors[1].setTradingPlayer(null);
            });
            server.runCommand("gamerule minecraft:mob_griefing false");
            server.runOnServer(s -> s.overworld().setBlockAndUpdate(crop, ((CropBlock)Blocks.WHEAT).getStateForAge(7)));
            context.waitTicks(60);
            server.runOnServer(s -> check(((CropBlock)Blocks.WHEAT).isMaxAge(s.overworld().getBlockState(crop)), "Farming respects mob griefing"));
            server.runCommand("time set 14000");
            context.waitTicks(60);
            server.runOnServer(s -> {
                check(actors[0].getMainHandItem().isEmpty() || actors[0].getMainHandItem().is(Items.BREAD),
                    "Farmer clears work tools outside work hours; evening bread sharing remains allowed: " + actors[0].getMainHandItem());
                check(actors[1].getMainHandItem().isEmpty(), "Fisherman clears rod outside work hours");
            });
            server.runCommand("time set 3000");
            ItemStack[] playerCatch = new ItemStack[1];
            int[] beforePlayerCatch = new int[1];
            net.minecraft.world.entity.projectile.FishingHook[] playerHook = new net.minecraft.world.entity.projectile.FishingHook[1];
            server.runOnServer(s -> {
                var player = world.getConnection().getServerPlayer();
                var level = s.overworld();
                player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(Items.FISHING_ROD));
                var hook = new net.minecraft.world.entity.projectile.FishingHook(player, level, 0, 0);
                hook.setPos(17.5, 100.87, .5);
                hook.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
                level.addFreshEntity(hook);
                playerHook[0] = hook;
                try {
                    var nibble = hook.getClass().getDeclaredField("nibble");
                    nibble.setAccessible(true);
                    nibble.setInt(hook, 60);
                } catch (ReflectiveOperationException error) {
                    throw new AssertionError("Could not prepare a vanilla player catch", error);
                }
            });
            server.runCommand("tp @a 19 103 -5 facing 17 101 0");
            server.runCommand("enchant @a luck_of_the_sea 1");
            context.waitTicks(12);
            context.takeScreenshot("player-bobber-fixed-front");
            server.runOnServer(s -> {
                var level = s.overworld();
                var cow = net.minecraft.world.entity.EntityTypes.COW.create(level,
                    net.minecraft.world.entity.EntitySpawnReason.COMMAND);
                cow.setPos(22, 101, -3);
                cow.setNoAi(true);
                level.addFreshEntity(cow);
                cow.setLeashedTo(world.getConnection().getServerPlayer(), true);
            });
            server.runCommand("tp @a 20 103 -7 facing 22 101 -3");
            context.waitTicks(4);
            context.takeScreenshot("player-lead-physics");

            server.runCommand("tp @a 19 103 -5 facing 17 101 0");
            context.waitTicks(2);
            context.runOnClient(client -> client.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK));
            context.waitTicks(2);
            net.minecraft.world.phys.Vec3[] animatedTip = new net.minecraft.world.phys.Vec3[1];
            context.runOnClient(client -> {
                animatedTip[0] = naturality.client.villager.PlayerRodTips.tips.get(client.player.getId());
                check(animatedTip[0] != null, "Third-person fishing line measures the rendered player rod");
            });
            context.runOnClient(client -> client.player.swing(
                net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true));
            context.waitTicks(2);
            context.runOnClient(client -> {
                var tip = naturality.client.villager.PlayerRodTips.tips.get(client.player.getId());
                check(tip != null && tip.distanceToSqr(animatedTip[0]) > .0001,
                    "Third-person rod endpoint moves with the player's swinging arm");
            });
            context.takeScreenshot("player-bobber-third-person-swing");
            context.runOnClient(client -> client.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON));
            server.runCommand("tp @a 16 102 -1 facing 17 101 0");
            context.waitTicks(2);
            context.takeScreenshot("player-bobber-fixed-side");
            server.runCommand("tp @a 18 105 0 facing 17 101 0");
            context.waitTicks(2);
            context.takeScreenshot("player-bobber-steep-line-glint");
            context.runOnClient(client -> client.options.keyUp.setDown(true));
            context.waitTicks(8);
            context.takeScreenshot("player-bobber-walking-rope");
            context.runOnClient(client -> client.options.keyUp.setDown(false));
            Object ropeTestKey = new Object();
            var ropeStart = new net.minecraft.world.phys.Vec3(17, 105, 0);
            var ropeEnd = new net.minecraft.world.phys.Vec3(20, 105, 0);
            net.minecraft.world.phys.Vec3[] ropeMiddle = new net.minecraft.world.phys.Vec3[1];
            context.runOnClient(client -> {
                var chain = naturality.client.villager.RopeChain.sample(ropeTestKey, ropeStart, ropeEnd);
                check(chain[0].equals(ropeStart) && chain[chain.length - 1].equals(ropeEnd),
                    "Physics rope pins both endpoints");
                ropeMiddle[0] = chain[chain.length / 2];
            });
            context.waitTicks(4);
            context.runOnClient(client -> {
                var movedEnd = ropeEnd.add(0, 0, 1);
                var chain = naturality.client.villager.RopeChain.sample(ropeTestKey, ropeStart, movedEnd);
                check(chain[chain.length - 1].equals(movedEnd), "Physics rope follows a moving endpoint");
                check(chain[chain.length / 2].distanceToSqr(ropeMiddle[0]) > .00001,
                    "Physics rope interior responds to movement and gravity");
                for (var link : chain) check(Double.isFinite(link.lengthSqr()), "Physics rope remains finite");
            });
            server.runCommand("tp @a 19 103 -5 facing 17 101 0");
            server.runOnServer(s -> {
                var player = world.getConnection().getServerPlayer();
                var level = s.overworld();
                playerHook[0].retrieve(new ItemStack(Items.FISHING_ROD));
                var returns = level.getEntitiesOfClass(naturality.villager.PlayerFishingReturn.class,
                    player.getBoundingBox().inflate(12), e -> e.owner() == player);
                check(!returns.isEmpty(), "Vanilla player catch gets a returning bobber and hooked item");
                check(returns.getFirst().rodGlint(), "Returning player bobber retains the enchanted rod glint");
                playerCatch[0] = returns.getFirst().catchItem().copy();
                beforePlayerCatch[0] = player.getInventory().countItem(playerCatch[0].getItem());
            });
            context.waitTicks(2);
            context.takeScreenshot("player-bobber-return");
            context.waitTicks(6);
            server.runOnServer(s -> {
                var player = world.getConnection().getServerPlayer();
                check(player.getInventory().countItem(playerCatch[0].getItem()) > beforePlayerCatch[0],
                    "Player receives the caught item when the bobber reaches them");
            });
            server.runCommand("tp @a 20 103 -7 facing 22 101 -3");
            server.runCommand("time set 18000");
            context.waitTicks(8);
            context.takeScreenshot("player-lead-lighting-night");
            server.runCommand("setblock 22 101 -4 torch");
            context.waitTicks(12);
            context.takeScreenshot("player-lead-lighting-torch");
            server.runCommand("setblock 22 101 -4 air");
            server.runCommand("time set 3000");
        }
    }

    private static void checkBatchFarming(ServerLevel level) {
        var job = new BlockPos(-12, 101, 1);
        level.setBlockAndUpdate(job, Blocks.COMPOSTER.defaultBlockState());
        var plots = java.util.List.of(new BlockPos(-12, 101, -1), new BlockPos(-11, 101, 0), new BlockPos(-13, 101, 0));
        for (int i = 0; i < plots.size(); i++) {
            level.setBlockAndUpdate(plots.get(i).below(), Blocks.FARMLAND.defaultBlockState().setValue(FarmlandBlock.MOISTURE, 7));
            level.setBlockAndUpdate(plots.get(i), ((CropBlock)Blocks.WHEAT).getStateForAge(i == 0 ? 7 : 6));
        }
        var farmer = worker(level, job, true);
        farmer.setNoAi(true);
        farmer.setPos(job.getX() + .5, job.getY(), job.getZ() - .7);
        farmer.setOnGround(true);
        var farmland = level.getBlockState(plots.getFirst().below());
        farmland.getBlock().fallOn(level, farmland, plots.getFirst().below(), farmer, 2.0);
        check(level.getBlockState(plots.getFirst().below()).is(Blocks.FARMLAND), "A falling villager preserves farmland");
        farmer.getInventory().removeItemType(Items.BONE_MEAL, 15);
        farmer.getInventory().addItem(new ItemStack(Items.BONE_MEAL, 2));
        farmer.getInventory().addItem(new ItemStack(Items.WHEAT_SEEDS, 6));
        var work = new ProfessionWork(true);
        long start = level.getGameTime();
        check(work.tryStart(level, farmer, start), "Batch farmer starts work");
        boolean exhausted = false;
        var harvestedAt = new java.util.HashMap<BlockPos, Integer>();
        boolean displayedSeeds = false;
        for (int tick = 0; tick < 300; tick++) {
            ItemStack heldBefore = farmer.getMainHandItem().copy();
            var lookBefore = farmer.getBrain().getMemory(MemoryModuleType.LOOK_TARGET);
            work.tickOrStop(level, farmer, start + tick);
            displayedSeeds |= farmer.getMainHandItem().is(Items.WHEAT_SEEDS);
            for (var plot : plots) {
                if (level.getBlockState(plot).isAir()) harvestedAt.putIfAbsent(plot, tick);
                else if (harvestedAt.containsKey(plot) && level.getBlockState(plot).is(Blocks.WHEAT)) {
                    check(tick - harvestedAt.remove(plot) >= 20, "Harvesting and replanting are separated by at least one second");
                    check(heldBefore.is(Items.WHEAT_SEEDS), "Farmer holds the planting seeds before placing the crop");
                    check(lookBefore.isPresent() && lookBefore.get().currentBlockPosition().equals(plot), "Farmer looks at the planting spot");
                }
            }
            if (!exhausted) {
                check(((CropBlock)Blocks.WHEAT).isMaxAge(level.getBlockState(plots.getFirst())), "Existing ripe crop waits while bone meal is used");
                if (farmer.getInventory().countItem(Items.BONE_MEAL) == 0) {
                    exhausted = true;
                    check(plots.stream().allMatch(p -> ((CropBlock)Blocks.WHEAT).isMaxAge(level.getBlockState(p))),
                        "Multiple crops mature before the first harvest and all bone meal is spent");
                }
            }
        }
        check(exhausted, "Farmer uses the entire bone meal supply across crops");
        check(displayedSeeds, "Planting has a visible seed holding phase");
        check(plots.stream().allMatch(p -> level.getBlockState(p).is(Blocks.WHEAT)
            && !((CropBlock)Blocks.WHEAT).isMaxAge(level.getBlockState(p))), "Farmer harvests and replants the entire batch");
        work.doStop(level, farmer, start + 300);
        farmer.discard();
        for (var p : plots) level.setBlockAndUpdate(p, Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(job, Blocks.AIR.defaultBlockState());
    }

    private static void checkEvenFertilizing(ServerLevel level) {
        var job = new BlockPos(-16, -30, -6);
        for (var p : BlockPos.betweenClosed(job.offset(-3, -1, -3), job.offset(3, 2, 3)))
            level.setBlockAndUpdate(p, p.getY() == job.getY() - 1 ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(job, Blocks.COMPOSTER.defaultBlockState());
        var plots = java.util.List.of(job.offset(0, 0, -2), job.offset(1, 0, -1), job.offset(-1, 0, -1));
        for (var p : plots) {
            level.setBlockAndUpdate(p.below(), Blocks.FARMLAND.defaultBlockState().setValue(FarmlandBlock.MOISTURE, 7));
            level.setBlockAndUpdate(p, ((CropBlock)Blocks.WHEAT).getStateForAge(0));
        }
        var farmer = worker(level, job, true);
        farmer.setNoAi(true);
        farmer.setPos(job.getX() + .5, job.getY(), job.getZ() - .7);
        farmer.setOnGround(true);
        farmer.getInventory().removeItemType(Items.BONE_MEAL, 15);
        farmer.getInventory().addItem(new ItemStack(Items.BONE_MEAL, 3));
        var work = new ProfessionWork(true);
        long start = level.getGameTime();
        check(work.tryStart(level, farmer, start), "Even-fertilizing farmer starts work");
        for (int tick = 0; tick < 180 && farmer.getInventory().countItem(Items.BONE_MEAL) > 0; tick++)
            work.tickOrStop(level, farmer, start + tick);
        check(farmer.getInventory().countItem(Items.BONE_MEAL) == 0, "Farmer spends all three bone meal");
        check(plots.stream().allMatch(p -> ((CropBlock)Blocks.WHEAT).getAge(level.getBlockState(p)) > 0),
            "Each crop receives one bone meal before any crop gets a second");
        work.doStop(level, farmer, start + 180);
        farmer.discard();
        for (var p : plots) level.setBlockAndUpdate(p, Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(job, Blocks.AIR.defaultBlockState());
    }

    private record CompostFixture(Villager farmer, ProfessionWork work, long start, BlockPos job) { }

    private static CompostFixture beginComposting(ServerLevel level) {
        var job = new BlockPos(-18, -30, 3);
        for (var p : BlockPos.betweenClosed(job.offset(-3, -1, -3), job.offset(3, 2, 3)))
            level.setBlockAndUpdate(p, p.getY() == job.getY() - 1 ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(job, Blocks.COMPOSTER.defaultBlockState());
        var farmer = worker(level, job, true);
        farmer.setNoAi(true);
        farmer.setPos(job.getX() + .5, job.getY(), job.getZ() - .7);
        farmer.setOnGround(true);
        farmer.getInventory().removeItemType(Items.BONE_MEAL, 15);
        farmer.getInventory().addItem(new ItemStack(Items.WHEAT_SEEDS, 128));
        var work = new ProfessionWork(true);
        long start = level.getGameTime();
        check(work.tryStart(level, farmer, start), "Composting farmer starts work");
        work.tickOrStop(level, farmer, start);
        check(level.getBlockState(job).getValue(ComposterBlock.LEVEL) == 7,
            "Surplus seeds fill the composter to the ready stage in one visit");
        check(farmer.getInventory().countItem(Items.WHEAT_SEEDS) >= 10, "Farmer reserves planting seeds");
        return new CompostFixture(farmer, work, start, job);
    }

    private static void finishComposting(ServerLevel level, CompostFixture fixture) {
        check(level.getBlockState(fixture.job()).getValue(ComposterBlock.LEVEL) == 8,
            "Filled composter ripens after its scheduled tick");
        fixture.work().tickOrStop(level, fixture.farmer(), fixture.start() + 600);
        check(fixture.farmer().getInventory().countItem(Items.BONE_MEAL) == 1,
            "Farmer collects the completed bone meal into its inventory");
        fixture.work().doStop(level, fixture.farmer(), fixture.start() + 600);
        fixture.farmer().discard();
        level.setBlockAndUpdate(fixture.job(), Blocks.AIR.defaultBlockState());
    }

    private static void checkStationPath(Villager villager, BlockPos job) {
        villager.setOnGround(true);
        var top = villager.getNavigation().createPath(job.above(), 0);
        check(top == null || !top.canReach(), "Villager cannot choose the top of its workstation as a destination");
        var around = villager.getNavigation().createPath(job.offset(0, 0, 3), 0);
        check(around != null && around.canReach(), "Villager can still path around its workstation");
        for (int i = 0; i < around.getNodeCount(); i++) {
            var node = around.getNode(i);
            check(node.x != job.getX() || node.y != job.getY() + 1 || node.z != job.getZ(),
                "Villager route must not step onto its workstation");
        }
    }

    private static Villager worker(ServerLevel level, BlockPos job, boolean farmer) {
        var body = new Villager(EntityTypes.VILLAGER, level);
        body.setPos(job.getX() + .5, job.getY(), job.getZ() - 1.5);
        body.setVillagerData(body.getVillagerData().withProfession(level.registryAccess(), farmer ? VillagerProfession.FARMER : VillagerProfession.FISHERMAN));
        body.finalizeSpawn(level, level.getCurrentDifficultyAt(job), EntitySpawnReason.STRUCTURE, null);
        body.setVillagerXp(1);
        body.getBrain().setMemory(MemoryModuleType.JOB_SITE, GlobalPos.of(level.dimension(), job));
        body.refreshBrain(level);
        level.addFreshEntity(body);
        return body;
    }
}
