package naturality.test;

import java.util.UUID;
import naturality.villager.*;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.*;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.village.ReputationEventType;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.villager.*;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.trading.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.storage.*;
import net.minecraft.util.ProblemReporter;

public final class ReputationGameTest implements FabricClientGameTest {
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static void score(ServerPlayer player, int value) {
        ReputationData.get(player.level().getServer()).change(player.getUUID(), value - Reputation.score(player));
    }
    private static Villager villager(ServerLevel level, net.minecraft.resources.ResourceKey<VillagerProfession> profession, double x) {
        var body = new Villager(EntityTypes.VILLAGER, level);
        body.setPos(x, 101, .5); body.setOnGround(true); body.setVillagerXp(1);
        body.setVillagerData(body.getVillagerData().withProfession(level.registryAccess(), profession));
        body.refreshBrain(level); level.addFreshEntity(body); return body;
    }
    private static ItemEntity drop(ServerLevel level, ServerPlayer donor, Villager body, ItemStack stack) {
        var item = new ItemEntity(level, body.getX(), 101, body.getZ(), stack);
        item.setThrower(donor); item.setNoPickUpDelay(); item.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        level.addFreshEntity(item); return item;
    }
    @Override public void runTest(ClientGameTestContext context) {
        net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave save;
        UUID[] playerId = new UUID[1], lineage = new UUID[1];
        Villager[] farmer = new Villager[1]; ItemEntity[] gift = new ItemEntity[1];
        try (var world = context.worldBuilder().create()) {
            save = world.getWorldSave(); var server = world.getServer();
            server.runCommand("gamemode creative @a"); server.runCommand("tp @a 0 104 -4");
            server.runCommand("time set 1000"); server.runCommand("gamerule minecraft:advance_time false");
            server.runCommand("fill -18 100 -18 18 106 18 air"); server.runCommand("fill -18 100 -18 18 100 18 stone");
            server.runOnServer(s -> {
                var level = s.overworld(); var player = world.getConnection().getServerPlayer(); playerId[0] = player.getUUID();
                check(Reputation.score(player) == 50, "Initial reputation is 50");
                farmer[0] = villager(level, VillagerProfession.FARMER, 2.5);
                gift[0] = drop(level, player, farmer[0], new ItemStack(Items.WHEAT_SEEDS, 4));
            });
            context.waitTicks(30);
            server.runOnServer(s -> {
                var level = s.overworld(); var player = world.getConnection().getServerPlayer(); var body = farmer[0];
                check(!gift[0].isAlive() && Reputation.score(player) == 53 && body.getInventory().countItem(Items.WHEAT_SEEDS) == 4,
                    "Actual seed gift pickup credits the throwing player once: " + Reputation.score(player));
                var emerald = drop(level, player, body, new ItemStack(Items.EMERALD));
                check(Reputation.acceptGift(level, body, emerald) && Reputation.score(player) == 53, "Single emerald gift gives no reputation");
                var emeraldStack = drop(level, player, body, new ItemStack(Items.EMERALD, 4));
                check(Reputation.acceptGift(level, body, emeraldStack) && Reputation.score(player) == 53, "Small emerald stack gives no reputation");
                var twenty = drop(level, player, body, new ItemStack(Items.EMERALD, 20));
                check(Reputation.acceptGift(level, body, twenty) && Reputation.score(player) == 54, "Twenty emeralds together give one reputation");
                var forty = drop(level, player, body, new ItemStack(Items.EMERALD, 40));
                check(Reputation.acceptGift(level, body, forty) && Reputation.score(player) == 56, "Forty emeralds together give two reputation");
                var fishing = villager(level, VillagerProfession.FISHERMAN, 5.5);
                var rod = new ItemStack(Items.FISHING_ROD);
                rod.enchant(level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.LURE), 1);
                var rodGift = drop(level, player, fishing, rod);
                check(Reputation.acceptGift(level, fishing, rodGift) && Reputation.score(player) == 59, "Better enchanted rod gives three reputation");
                check(Reputation.state(fishing).rod.isEnchanted(), "Gifted fishing rod retained for work");
                var equalRod = drop(level, player, fishing, rod.copy());
                check(!Reputation.acceptGift(level, fishing, equalRod) && Reputation.score(player) == 59, "Equal rod does not earn another reward");
                equalRod.discard(); fishing.discard();
                var offers = new MerchantOffers();
                var offer = new MerchantOffer(new ItemCost(Items.EMERALD, 20), new ItemStack(Items.BREAD), 10, 1, .05F);
                offers.add(offer); body.setOffers(offers);
                for (int reputation : new int[]{50, 100, 11}) {
                    score(player, reputation); body.mobInteract(player, InteractionHand.MAIN_HAND);
                    check(body.getTradingPlayer() == player, "Trading remains available above ten");
                    check(offer.getCostA().getCount() == 20 + Reputation.priceDiff(20, reputation), "Price follows shared reputation");
                    player.closeContainer();
                }
                score(player, 10); body.mobInteract(player, InteractionHand.MAIN_HAND);
                check(body.getTradingPlayer() == null, "Trading refused at ten");
                score(player, 50); body.mobInteract(player, InteractionHand.MAIN_HAND);
                var menu = (MerchantMenu)player.containerMenu;
                menu.getSlot(0).set(new ItemStack(Items.EMERALD, 20));
                menu.clicked(2, 0, ContainerInput.PICKUP, player);
                check(Reputation.score(player) == 50, "Completed trade earns no reputation"); player.closeContainer();
                score(player, 100); Reputation.change(player, 1);
                check(player.hasEffect(MobEffects.HERO_OF_THE_VILLAGE) && player.getEffect(MobEffects.HERO_OF_THE_VILLAGE).getDuration() == 1200,
                    "Gain at existing maximum gives one minute of hero");
                player.removeEffect(MobEffects.HERO_OF_THE_VILLAGE);
                score(player, 50);
                player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                body.hurtServer(level, player.damageSources().playerAttack(player), 1);
                check(Reputation.score(player) == 48, "Unarmed hit costs two");
                player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
                body.hurtServer(level, player.damageSources().playerAttack(player), 2);
                check(Reputation.score(player) == 43, "Armed hit costs five");
                body.hurtServer(level, player.damageSources().playerAttack(player), 100);
                check(Reputation.score(player) == 20, "Lethal attack sets reputation to twenty without an extra hit penalty");
                for (int starting : new int[]{0, 10, 20, 50, 100}) {
                    score(player, starting);
                    var victim = villager(level, VillagerProfession.FARMER, 9.5);
                    victim.setNoAi(true);
                    victim.hurtServer(level, player.damageSources().playerAttack(player), 100);
                    check(Reputation.score(player) == (starting <= 20 ? 0 : 20), "Villager kill applies the threshold rule from " + starting);
                    check(!player.hasEffect(MobEffects.HERO_OF_THE_VILLAGE), "Setting kill reputation is not a positive reputation reward");
                }
                var golem = EntityTypes.IRON_GOLEM.create(level, EntitySpawnReason.COMMAND);
                golem.setPos(5, 101, 3); level.addFreshEntity(golem); score(player, 50);
                golem.hurtServer(level, player.damageSources().playerAttack(player), 1000);
                check(Reputation.score(player) == 40, "Killing a golem costs ten");
                var original = villager(level, VillagerProfession.FARMER, 8.5); original.setNoAi(true);
                lineage[0] = Reputation.state(original).lineage; score(player, 50);
                original.onReputationEventFrom(ReputationEventType.ZOMBIE_VILLAGER_CURED, player);
                check(Reputation.score(player) == 60, "First cure gives ten");
                var zombie = original.convertTo(EntityTypes.ZOMBIE_VILLAGER, ConversionParams.single(original, false, false), converted -> { });
                var cured = zombie.convertTo(EntityTypes.VILLAGER, ConversionParams.single(zombie, false, false), converted ->
                    converted.onReputationEventFrom(ReputationEventType.ZOMBIE_VILLAGER_CURED, player));
                check(Reputation.state(cured).lineage.equals(lineage[0]) && Reputation.score(player) == 60,
                    "Conversion callback retains lineage before cure reward; repeat cure gives nothing"); cured.discard();
                var nitwit = villager(level, VillagerProfession.NITWIT, 7.5);
                score(player, 50); nitwit.mobInteract(player, InteractionHand.MAIN_HAND);
                check(nitwit.getUnhappyCounter() > 0, "Neutral nitwit shakes head");
                score(player, 51); nitwit.mobInteract(player, InteractionHand.MAIN_HAND);
                check(nitwit.getTradingPlayer() == null, "Friendly nitwit remains a reputation display"); nitwit.discard();
                score(player, 50);
                var chestPos = new BlockPos(10, 101, 0); level.setBlockAndUpdate(chestPos, Blocks.CHEST.defaultBlockState());
                var chest = (ChestBlockEntity)level.getBlockEntity(chestPos);
                chest.setLootTable(ResourceKey.create(Registries.LOOT_TABLE, Identifier.withDefaultNamespace("chests/village/village_weaponsmith")));
                chest.setLootTable(null); chest.setItem(0, new ItemStack(Items.EMERALD, 5));
                check(((VillageChest)chest).naturality$isVillageChest(), "Village chest provenance survives loot unpacking");
                player.openMenu(chest); check(Reputation.score(player) == 50, "Opening a chest is not stealing");
                player.containerMenu.clicked(0, 0, ContainerInput.QUICK_MOVE, player);
                check(Reputation.score(player) == 47 && chest.getItem(0).isEmpty(), "Shift-click theft costs three"); player.closeContainer();
                var ordinaryPos = new BlockPos(12, 101, 0); level.setBlockAndUpdate(ordinaryPos, Blocks.CHEST.defaultBlockState());
                var ordinary = (ChestBlockEntity)level.getBlockEntity(ordinaryPos); ordinary.setItem(0, new ItemStack(Items.EMERALD));
                player.openMenu(ordinary); player.containerMenu.clicked(0, 0, ContainerInput.PICKUP, player);
                check(Reputation.score(player) == 47, "Player-placed chest withdrawal has no penalty"); player.closeContainer();
            });
            server.runCommand("time set 18000");
            server.runOnServer(s -> {
                var level = s.overworld(); var player = world.getConnection().getServerPlayer(); score(player, 50);
                var sleeper = villager(level, VillagerProfession.FARMER, .5); sleeper.setNoAi(true);
                var bed = new BlockPos(0, 101, 5);
                level.setBlockAndUpdate(bed, Blocks.BED.pick(DyeColor.RED).defaultBlockState()
                    .setValue(net.minecraft.world.level.block.AbstractBedBlock.PART, net.minecraft.world.level.block.state.properties.BedPart.HEAD));
                level.setBlockAndUpdate(bed.south(), Blocks.BED.pick(DyeColor.RED).defaultBlockState());
                sleeper.startSleeping(bed);
                level.getBlockState(bed).useWithoutItem(level, player,
                    new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(bed), Direction.UP, bed, false));
                check(!sleeper.isSleeping() && Reputation.score(player) == 49, "Actually waking a sleeper costs one");
                check(Reputation.tradeBlocked(sleeper, player), "Force-woken villager refuses until morning");
                var output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess()); sleeper.saveWithoutId(output);
                var restored = new Villager(EntityTypes.VILLAGER, level);
                restored.load(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), output.buildResult()));
                check(Reputation.tradeBlocked(restored, player), "Wake refusal persists across entity reload");
                score(player, 50); sleeper.startSleeping(bed);
                player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                sleeper.hurtServer(level, player.damageSources().playerAttack(player), 1);
                check(Reputation.score(player) == 47 && Reputation.tradeBlocked(sleeper, player), "A hit also records forced waking and morning trade refusal");
                sleeper.discard();
            });
            server.runCommand("time set 24000"); server.runCommand("gamemode survival @a");
            server.runOnServer(s -> {
                var level = s.overworld(); var player = world.getConnection().getServerPlayer();
                var body = villager(level, VillagerProfession.FARMER, 2.5);
                Reputation.state(body).wakeUntil = 24000;
                check(!Reputation.tradeBlocked(body, player), "Morning clears wake refusal");
                var pillager = EntityTypes.PILLAGER.create(level, EntitySpawnReason.COMMAND); pillager.setNoAi(true);
                score(player, 10); check(!pillager.canAttack(player), "Illagers ignore players at ten");
                pillager.setTarget(player); check(pillager.getTarget() == null, "Direct retaliation target also rejected");
                score(player, 11); check(pillager.canAttack(player), "Illagers hostile above ten");
                score(player, 0);
                var golem = EntityTypes.IRON_GOLEM.create(level, EntitySpawnReason.COMMAND); golem.setPos(5, 101, 0);
                golem.tickCount = 20; level.addFreshEntity(golem); Reputation.react(level, golem);
                check(golem.getTarget() == player, "Village golem targets zero-reputation player");
                score(player, 1); Reputation.react(level, golem);
                check(golem.getTarget() == null, "Reputation hostility clears after recovering above zero"); score(player, 0);
                var built = EntityTypes.IRON_GOLEM.create(level, EntitySpawnReason.COMMAND); built.setPlayerCreated(true); built.tickCount = 20;
                Reputation.react(level, built); check(built.getTarget() == null, "Player-built golem excluded from reputation hostility");
                body.tickCount = 10; Reputation.react(level, body);
                check(Reputation.state(body).fleeing && body.getBrain().hasMemoryValue(net.minecraft.world.entity.ai.memory.MemoryModuleType.WALK_TARGET),
                    "Villager flees a zero-reputation player");
                body.getBrain().setActiveActivityIfPossible(net.minecraft.world.entity.schedule.Activity.WORK);
                body.getBrain().setMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.WALK_TARGET,
                    new net.minecraft.world.entity.ai.memory.WalkTarget(new BlockPos(3, 101, 0), .5F, 0));
                Reputation.react(level, body);
                check(body.getBrain().isActive(net.minecraft.world.entity.schedule.Activity.PANIC), "Reputation fear overrides work immediately");
                var bed = new BlockPos(0, 101, 5);
                body.startSleeping(bed);
                Reputation.react(level, body);
                check(!body.isSleeping() && body.getBrain().isActive(net.minecraft.world.entity.schedule.Activity.PANIC),
                    "Reputation fear interrupts sleeping immediately");
                score(player, 1); Reputation.react(level, body);
                check(!Reputation.state(body).fleeing && !body.getBrain().hasMemoryValue(net.minecraft.world.entity.ai.memory.MemoryModuleType.NEAREST_HOSTILE),
                    "Recovery clears the artificial threat and permits normal activity");
                body.discard(); golem.discard(); built.discard(); pillager.discard();
                score(player, 50);
                var theftPos = new BlockPos(10, 101, 0);
                var chest = (ChestBlockEntity)level.getBlockEntity(theftPos); chest.setItem(0, new ItemStack(Items.EMERALD));
                player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                check(player.gameMode.destroyBlock(theftPos) && Reputation.score(player) == 47, "Breaking a stocked village chest also counts as theft");
                var donor = UUID.randomUUID(); var data = ReputationData.get(s);
                Reputation.credit(level, donor, 3); check(data.score(donor) == 53, "Offline donor receives gift reputation");
                data.change(donor, 47); Reputation.credit(level, donor, 1);
                check(data.takeHero(donor) && !data.takeHero(donor), "Offline maximum-score gift queues one hero reward");
                score(player, 50);
                var tired = villager(level, VillagerProfession.FARMER, 0.5);
                var sleepState = Reputation.state(tired);
                long now = level.getOverworldClockTime();
                sleepState.lastSleep = now;
                Reputation.change(player, -1, tired.position());
                check(tired.getUnhappyCounter() == 40, "Nearby villagers react angrily to reputation penalties");
                score(player, 50);
                check(!Reputation.tradeBlocked(tired, player), "Rested villager can trade");
                sleepState.lastSleep = 0;
                check(Reputation.tradeBlocked(tired, player), "One day without sleep blocks trading");
                var dispatcher = s.getCommands().getDispatcher();
                try { dispatcher.execute("reputation 87", s.createCommandSourceStack().withEntity(player)); }
                catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) { throw new AssertionError(e); }
                check(Reputation.score(player) == 87, "Reputation self command changes score");
                try { dispatcher.execute("reputation set @a 73", s.createCommandSourceStack()); }
                catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) { throw new AssertionError(e); }
                check(Reputation.score(player) == 73, "Reputation selector command changes score");
                tired.discard();
                var raidCenter = new BlockPos(0, 101, 5);
                var pois = level.getPoiManager();
                pois.add(raidCenter, level.registryAccess().lookupOrThrow(Registries.POINT_OF_INTEREST_TYPE)
                    .getOrThrow(net.minecraft.world.entity.ai.village.poi.PoiTypes.HOME));
                pois.take(type -> type.is(net.minecraft.world.entity.ai.village.poi.PoiTypes.HOME),
                    (type, pos) -> pos.equals(raidCenter), raidCenter, 1);
                for (int starting : new int[]{50, 90}) {
                    score(player, starting);
                    var encoded = net.minecraft.world.entity.raid.Raid.MAP_CODEC.codec().encodeStart(com.mojang.serialization.JsonOps.INSTANCE,
                        new net.minecraft.world.entity.raid.Raid(raidCenter, Difficulty.NORMAL)).getOrThrow().getAsJsonObject();
                    encoded.addProperty("started", true);
                    encoded.addProperty("groups_spawned", 1);
                    encoded.addProperty("group_count", 1);
                    encoded.addProperty("post_raid_ticks", 40);
                    encoded.addProperty("cooldown_ticks", 0);
                    var raid = net.minecraft.world.entity.raid.Raid.MAP_CODEC.codec().parse(com.mojang.serialization.JsonOps.INSTANCE, encoded).getOrThrow();
                    raid.addHeroOfTheVillage(player);
                    raid.tick(level);
                    check(raid.isVictory() && Reputation.score(player) == Math.min(100, starting + 20), "Raid victory grants twenty reputation, capped at one hundred");
                    raid.tick(level);
                    check(Reputation.score(player) == Math.min(100, starting + 20), "Raid celebration does not repeat reputation reward");
                    var savedRaid = net.minecraft.world.entity.raid.Raid.MAP_CODEC.codec().encodeStart(com.mojang.serialization.JsonOps.INSTANCE, raid).getOrThrow();
                    var restoredRaid = net.minecraft.world.entity.raid.Raid.MAP_CODEC.codec().parse(com.mojang.serialization.JsonOps.INSTANCE, savedRaid).getOrThrow();
                    score(player, 50);
                    restoredRaid.tick(level);
                    check(Reputation.score(player) == 50, "Reloading a completed raid does not repeat reward");
                    var stopped = new net.minecraft.world.entity.raid.Raid(raidCenter, Difficulty.NORMAL);
                    stopped.addHeroOfTheVillage(player); stopped.stop(); stopped.tick(level);
                    check(Reputation.score(player) == 50, "Stopped raid does not grant victory reputation");
                }
                score(player, 73);
            });
        }
        try (var world = save.open()) {
            var server = world.getServer();
            server.runCommand("gamemode creative @a");
            server.runCommand("tp @a 0 104 -4");
            server.runCommand("time set 72000");
            Villager[] exhausted = new Villager[1];
            server.runOnServer(s -> {
                var level = s.overworld();
                var body = villager(level, VillagerProfession.FARMER, .5);
                exhausted[0] = body;
                Reputation.state(body).lastSleep = 0;
                check(VillagerSleep.tick(level, body), "Two missed days override normal AI");
                check(body.getBrain().isActive(net.minecraft.world.entity.schedule.Activity.REST), "Exhausted villager stops working to seek bed");
                var bed = new BlockPos(0, 101, 5);
                level.setBlockAndUpdate(bed, Blocks.BED.pick(DyeColor.RED).defaultBlockState()
                    .setValue(net.minecraft.world.level.block.AbstractBedBlock.PART, net.minecraft.world.level.block.state.properties.BedPart.HEAD));
                body.setPos(.5, 101, 4.5);
                Reputation.state(body).sleepBed = bed;
                VillagerSleep.tick(level, body);
                check(body.isSleeping() && Reputation.state(body).recoveryUntil == 96000, "Exhausted villager sleeps during day until next morning");
                var output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
                body.saveWithoutId(output);
                var restored = new Villager(EntityTypes.VILLAGER, level);
                restored.load(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), output.buildResult()));
                check(Reputation.state(restored).recoveryUntil == 96000, "Recovery sleep deadline persists");
            });
            context.waitTicks(20);
            server.runOnServer(s -> check(exhausted[0].isSleeping(), "Daytime schedule does not interrupt recovery sleep"));
            server.runCommand("time set 96000");
            server.runOnServer(s -> {
                VillagerSleep.tick(s.overworld(), exhausted[0]);
                check(!exhausted[0].isSleeping() && !Reputation.tradeBlocked(exhausted[0], world.getConnection().getServerPlayer()),
                    "Next morning wakes rested villager and restores trade");
                exhausted[0].discard();
            });
            world.getServer().runOnServer(s -> {
                var data = ReputationData.get(s);
                check(data.score(playerId[0]) == 73, "Shared reputation survives world reopen");
                check(!data.firstCure(lineage[0]), "Cure reward limit survives world reopen");
            });
        }
    }
}
