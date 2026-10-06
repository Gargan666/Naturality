package naturality.villager;

import com.google.common.collect.ImmutableMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.component.DataComponents;
import naturality.util.LoadedChunks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.behavior.WorkAtComposter;
import net.minecraft.world.entity.ai.behavior.WorkAtPoi;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BoneMealItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ComposterBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** One work controller per brain. The core brain still owns movement, panic and trading. */
public final class ProfessionWork extends Behavior<Villager> {
    private enum Task { HARVEST, PLANT, GROW, FISH, STATION }
    private record Target(BlockPos pos, BlockPos stand, Task task) { }
    private final boolean farmer;
    private boolean harvestingBatch;
    private boolean servingButcher;
    private final Map<BlockPos, Long> replantAfter = new HashMap<>();
    private final Map<BlockPos, Integer> fertilizedThisRound = new HashMap<>();
    private Target target;
    private Target fishingSpot;
    private BlockPos fishingJob;
    private ServerLevel fishingLevel;
    private long nextSearch, nextAction, nextStation, nextCastReady, targetSince;
    private int blockedFishingTicks;
    private ItemStack previousHand = ItemStack.EMPTY;
    private ItemStack tool = ItemStack.EMPTY;
    private VillagerBobber bobber;
    private final Map<BlockPos, Long> unreachableUntil = new HashMap<>();
    private final ComposterWork composter = new ComposterWork();
    private final StationWork station = new StationWork();

    public ProfessionWork(boolean farmer) {
        super(ImmutableMap.of(MemoryModuleType.JOB_SITE, MemoryStatus.VALUE_PRESENT), 1200);
        this.farmer = farmer;
    }

    @Override protected boolean checkExtraStartConditions(ServerLevel level, Villager body) {
        return available(body) && body.getBrain().getMemory(MemoryModuleType.JOB_SITE)
            .filter(p -> p.dimension().equals(level.dimension())).isPresent();
    }

    private boolean available(Villager body) {
        return !body.isBaby() && !body.isTrading() && body.getBrain().isActive(Activity.WORK)
            && body.getVillagerData().profession().is(farmer ? VillagerProfession.FARMER : VillagerProfession.FISHERMAN);
    }

    @Override protected boolean canStillUse(ServerLevel level, Villager body, long time) {
        return checkExtraStartConditions(level, body);
    }

    @Override protected void start(ServerLevel level, Villager body, long time) {
        previousHand = body.getMainHandItem();
        target = null;
        nextSearch = time;
    }

    @Override protected void stop(ServerLevel level, Villager body, long time) {
        equip(body, ItemStack.EMPTY);
        clearTarget(body);
    }

    private void equip(Villager body, ItemStack stack) {
        if (!tool.isEmpty() && body.getMainHandItem() == tool) body.setItemSlot(EquipmentSlot.MAINHAND, previousHand);
        tool = stack;
        if (!stack.isEmpty()) body.setItemSlot(EquipmentSlot.MAINHAND, stack);
    }

    private void clearTarget(Villager body) {
        if (bobber != null && !bobber.isRemoved()) bobber.discard();
        bobber = null;
        if (target != null) {
            body.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
            body.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
            body.getNavigation().stop();
        }
        target = null;
        blockedFishingTicks = 0;
        ((VillagerWorkVisuals)body).naturality$setCastTarget(null);
    }

    @Override protected void tick(ServerLevel level, Villager body, long time) {
        if (farmer) {
            ItemStack delivery = FarmerSupplies.display(body);
            if (!delivery.isEmpty()) {
                clearTarget(body);
                equip(body, delivery);
                servingButcher = true;
                FarmerSupplies.lookAtRecipient(body);
                return;
            }
            if (servingButcher) { servingButcher = false; equip(body, ItemStack.EMPTY); nextSearch = time; }
        }
        if (target != null && (!LoadedChunks.has(level, target.pos) || time - targetSince > 1000)) {
            if (target.task == Task.FISH && !target.stand.closerToCenterThan(body.position(), 1.2))
                forgetFishingSpot(time);
            clearTarget(body);
            equip(body, ItemStack.EMPTY);
            nextSearch = time + 100;
        }
        if (target == null) {
            if (time < nextSearch) return;
            // Navigation cannot plan before a freshly spawned villager has landed.
            if (!body.onGround()) return;
            nextSearch = time + 100;
            var job = body.getBrain().getMemory(MemoryModuleType.JOB_SITE).orElseThrow().pos();
            if (time >= nextStation) {
                if (LoadedChunks.has(level, job)) target = findStation(level, body, job);
                // A failed station visit must not cancel every subsequent fishing cast.
                if (!farmer && target == null) nextStation = time + 100;
            }
            if (target == null) target = farmer ? findFarm(level, body, time) : findFishing(level, body);
            if (target == null) return;
            targetSince = time;
            nextAction = 0;
            if (target.task == Task.PLANT) {
                var seeds = plantingSeed(level, body, target.pos);
                if (seeds.isEmpty()) { clearTarget(body); return; }
                equip(body, seeds);
                nextAction = time + 12;
            }
        }
        if (target.task != Task.STATION && farmer && !level.getGameRules().get(GameRules.MOB_GRIEFING)) {
            clearTarget(body);
            equip(body, ItemStack.EMPTY);
            return;
        }
        if (target.task == Task.FISH && (!restingWater(level, target.pos)
                || !level.getBlockState(target.stand.below()).isFaceSturdy(level, target.stand.below(), Direction.UP)
                || !level.getBlockState(target.stand).isAir() || !level.getBlockState(target.stand.above()).isAir())) {
            clearTarget(body);
            equip(body, ItemStack.EMPTY);
            return;
        }
        body.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(target.pos));
        if (target.task == Task.PLANT) {
            var point = Vec3.atCenterOf(target.pos);
            body.getLookControl().setLookAt(point.x, point.y, point.z);
        }
        double reach = target.task == Task.FISH || target.task == Task.STATION || target.task == Task.HARVEST ? 1.2 : 2.3;
        boolean atStand = target.stand.closerToCenterThan(body.position(), reach);
        boolean canSee = !atStand || (target.task == Task.FISH
            ? visibleWater(level, body, body.getEyePosition(), target.pos) : visible(level, body, target.pos));
        if (target.task == Task.FISH && atStand && !canSee) {
            // Allow navigation to finish centering on the bank, then try another bank.
            if (++blockedFishingTicks >= 40) {
                forgetFishingSpot(time);
                clearTarget(body);
                equip(body, ItemStack.EMPTY);
                nextSearch = time + 20;
                return;
            }
        } else blockedFishingTicks = 0;
        if (!atStand
                || target.task == Task.STATION && !target.pos.closerToCenterThan(body.position(), 1.73)
                || !canSee
                || target.task == Task.FISH && body.isInWater()) {
            if (time % 20 == 0 || time == targetSince)
                body.getBrain().setMemory(MemoryModuleType.WALK_TARGET,
                    new WalkTarget(target.stand, 0.5F,
                        target.task == Task.FISH || target.task == Task.STATION || target.task == Task.HARVEST ? 0 : 1));
            if (target.task != Task.PLANT) nextAction = 0;
            if (target.task == Task.FISH) {
                if (bobber != null) bobber.discard();
                bobber = null;
                ((VillagerWorkVisuals)body).naturality$setCastTarget(null);
            }
            return;
        }
        if (target.task == Task.FISH) {
            var giftedRod = Reputation.state(body).rod;
            if (tool.isEmpty() || !giftedRod.isEmpty() && !ItemStack.isSameItemSameComponents(tool, giftedRod))
                equip(body, giftedRod.isEmpty() ? new ItemStack(Items.FISHING_ROD) : giftedRod.copy());
            if (bobber != null && bobber.isRemoved()) {
                if (bobber.phase() == VillagerBobber.FLYING) nextCastReady = time + 20;
                bobber = null;
                ((VillagerWorkVisuals)body).naturality$setCastTarget(null);
            }
            if (bobber == null) {
                if (time < nextCastReady) return;
                if (time >= nextStation) {
                    clearTarget(body);
                    equip(body, ItemStack.EMPTY);
                    nextSearch = time;
                    return;
                }
                body.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT);
                level.playSound(null, body.blockPosition(), SoundEvents.FISHING_BOBBER_THROW, SoundSource.NEUTRAL,
                    .5F, .4F / (body.getRandom().nextFloat() * .4F + .8F));
                bobber = new VillagerBobber(level, body, target.pos);
                level.addFreshEntity(bobber);
                ((VillagerWorkVisuals)body).naturality$setCastTarget(target.pos);
                nextAction = time + catchDelay(body);
            }
            if (bobber.phase() == VillagerBobber.BOBBING) {
                long remaining = nextAction - time;
                if (remaining > 0 && remaining <= 40 && time % 3 == 0) {
                    double angle = body.getRandom().nextDouble() * Math.PI * 2;
                    double distance = remaining * .065;
                    double x = bobber.getX() + Math.cos(angle) * distance;
                    double z = bobber.getZ() + Math.sin(angle) * distance;
                    level.sendParticles(ParticleTypes.FISHING, x, bobber.getY() + .08, z, 1, 0, 0, 0, 0);
                    level.sendParticles(ParticleTypes.BUBBLE, x, bobber.getY() - .05, z, 1, 0, 0, 0, 0);
                }
            }
            if (bobber.phase() == VillagerBobber.BOBBING && time >= nextAction) {
                bobber.bite(catchFromVanillaLoot(level, body, bobber));
                level.sendParticles(ParticleTypes.SPLASH, bobber.getX(), bobber.getY(), bobber.getZ(), 8, .2, .1, .2, .03);
                level.sendParticles(ParticleTypes.BUBBLE, bobber.getX(), bobber.getY() - .1, bobber.getZ(), 8, .2, .05, .2, .03);
                level.playSound(null, bobber.blockPosition(), SoundEvents.FISHING_BOBBER_SPLASH, SoundSource.NEUTRAL, .5F, 1);
                nextAction = time + 20;
            } else if (bobber.phase() == VillagerBobber.BITING && time >= nextAction) {
                body.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT);
                level.playSound(null, body.blockPosition(), SoundEvents.FISHING_BOBBER_RETRIEVE, SoundSource.NEUTRAL,
                    1.0F, .4F / (body.getRandom().nextFloat() * .4F + .8F));
                bobber.retrieve();
                ((VillagerWorkVisuals)body).naturality$setCastTarget(null);
                nextAction = 0;
                nextCastReady = time + 15;
            }
            return;
        }
        if (target.task == Task.GROW && tool.isEmpty()) {
            equip(body, new ItemStack(Items.BONE_MEAL));
            nextAction = time + 12;
        }
        if (time < nextAction) return;
        var pos = target.pos;
        var state = level.getBlockState(pos);
        switch (target.task) {
            case STATION -> {
                if (farmer && state.is(Blocks.COMPOSTER) && level.getGameRules().get(GameRules.MOB_GRIEFING)) composter.perform(level, body, time);
                else station.perform(level, body, time);
                nextStation = time + 600;
            }
            case HARVEST -> {
                if (state.getBlock() instanceof CropBlock crop && crop.isMaxAge(state)) {
                    level.destroyBlock(pos, true, body);
                    replantAfter.put(pos.immutable(), time + 20);
                    body.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT);
                }
            }
            case PLANT -> {
                if (time >= replantAfter.getOrDefault(pos, 0L) && plant(level, body, pos, tool))
                    body.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT);
            }
            case GROW -> {
                if (state.getBlock() instanceof CropBlock crop && !crop.isMaxAge(state)) {
                    for (int i = 0; i < body.getInventory().getContainerSize(); i++) {
                        var stack = body.getInventory().getItem(i);
                        if (stack.is(Items.BONE_MEAL) && BoneMealItem.growCrop(stack, level, pos)) {
                            fertilizedThisRound.merge(pos, 1, Integer::sum);
                            body.getInventory().setChanged();
                            level.levelEvent(1505, pos, 15);
                            body.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT);
                            break;
                        }
                    }
                }
            }
            default -> { }
        }
        equip(body, ItemStack.EMPTY);
        clearTarget(body);
        nextSearch = time + 20;
    }

    public static int catchDelay(Villager body) { return 200 + body.getRandom().nextInt(101); }

    /** Roll the same fishing loot table as a player; the result exists only on the visual hook. */
    private static ItemStack catchFromVanillaLoot(ServerLevel level, Villager body, VillagerBobber bobber) {
        // Treasure entries inspect THIS_ENTITY's open-water flag, so use vanilla's
        // own hook and open-water test for loot context without spawning it.
        var lootHook = new FishingHook(EntityTypes.FISHING_BOBBER, level);
        lootHook.setPos(bobber.getX(), bobber.getY(), bobber.getZ());
        var hookAccess = (naturality.mixin.FishingHookLootAccess)lootHook;
        hookAccess.naturality$setOpenWater(hookAccess.naturality$calculateOpenWater(bobber.blockPosition()));
        var params = new LootParams.Builder(level)
            .withParameter(LootContextParams.ORIGIN, bobber.position())
            .withParameter(LootContextParams.TOOL, body.getMainHandItem())
            .withParameter(LootContextParams.THIS_ENTITY, lootHook)
            .create(LootContextParamSets.FISHING);
        var items = level.getServer().reloadableRegistries().getLootTable(BuiltInLootTables.FISHING).getRandomItems(params);
        return items.isEmpty() ? ItemStack.EMPTY : items.get(body.getRandom().nextInt(items.size())).copyWithCount(1);
    }

    private Target findFarm(ServerLevel level, Villager body, long time) {
        replantAfter.entrySet().removeIf(entry -> entry.getValue() <= time);
        if (!level.getGameRules().get(GameRules.MOB_GRIEFING)) return null;
        List<Target> candidates = new ArrayList<>();
        boolean seeds = body.hasFarmSeeds(), meal = body.getInventory().countItem(Items.BONE_MEAL) > 0;
        var origin = body.blockPosition();
        for (var p : BlockPos.betweenClosed(origin.offset(-8, -3, -8), origin.offset(8, 3, 8))) {
            if (!LoadedChunks.has(level, p)) continue;
            var state = level.getBlockState(p);
            Task task = null;
            if (state.getBlock() instanceof CropBlock crop) task = crop.isMaxAge(state) ? Task.HARVEST : meal ? Task.GROW : null;
            else if (seeds && state.isAir() && level.getBlockState(p.below()).is(Blocks.FARMLAND)
                    && time >= replantAfter.getOrDefault(p, 0L) && !plantingSeed(level, body, p).isEmpty()) task = Task.PLANT;
            if (task != null) candidates.add(new Target(p.immutable(), p.immutable(), task));
        }
        candidates.sort(Comparator.comparingDouble(t -> t.pos.distSqr(origin)));
        // Finish fertilizing the field before harvesting, then finish that harvest
        // before using new supplies on the freshly replanted plots.
        if (!harvestingBatch && meal) {
            var growing = firstReachable(body, growingByAge(level, candidates));
            if (growing != null) return growing;
        }
        var ripe = firstReachable(body, candidates.stream().filter(t -> t.task == Task.HARVEST).toList());
        if (ripe != null) {
            harvestingBatch = true;
            return ripe;
        }
        harvestingBatch = false;
        fertilizedThisRound.clear();
        var growing = firstReachable(body, growingByAge(level, candidates));
        return growing != null ? growing : firstReachable(body, candidates.stream().filter(t -> t.task == Task.PLANT).toList());
    }

    private List<Target> growingByAge(ServerLevel level, List<Target> candidates) {
        var growing = new ArrayList<>(candidates.stream().filter(t -> t.task == Task.GROW).toList());
        growing.sort(Comparator.<Target>comparingInt(t -> fertilizedThisRound.getOrDefault(t.pos, 0))
            .thenComparingInt(t -> ((CropBlock)level.getBlockState(t.pos).getBlock()).getAge(level.getBlockState(t.pos))));
        return growing;
    }

    private Target findFishing(ServerLevel level, Villager body) {
        var job = body.getBrain().getMemory(MemoryModuleType.JOB_SITE).orElseThrow().pos();
        if (fishingLevel != level || !job.equals(fishingJob)) {
            fishingSpot = null;
            fishingLevel = level;
            fishingJob = job;
        }
        // Keep a successful spot through station visits, trades and work restarts.
        if (fishingSpot != null && FishingSpots.usable(level, new FishingSpots.Spot(fishingSpot.pos, fishingSpot.stand))
                && canCastFromBank(level, body, fishingSpot)) {
            if (fishingSpot.stand.closerToCenterThan(body.position(), 1.2) && !body.isInWater()) return fishingSpot;
            Target reachable = firstReachable(body, List.of(fishingSpot));
            if (reachable != null) return reachable;
        }
        fishingSpot = null;
        List<Target> candidates = new ArrayList<>();
        for (var spot : FishingSpots.nearby(level, job))
            candidates.add(new Target(spot.water(), spot.stand(), Task.FISH));
        candidates.sort(Comparator.comparingDouble(t -> t.stand.distSqr(body.blockPosition())));
        fishingSpot = firstReachable(body, candidates);
        return fishingSpot;
    }

    private Target findStation(ServerLevel level, Villager body, BlockPos job) {
        List<Target> candidates = new ArrayList<>();
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            var stand = job.relative(dir);
            if (LoadedChunks.has(level, stand) && level.getBlockState(stand).isAir()
                    && level.getBlockState(stand.above()).isAir()
                    && level.getBlockState(stand.below()).isFaceSturdy(level, stand.below(), Direction.UP))
                candidates.add(new Target(job, stand, Task.STATION));
        }
        candidates.sort(Comparator.comparingDouble(t -> t.stand.distSqr(body.blockPosition())));
        return firstReachable(body, candidates);
    }

    public static boolean restingWater(ServerLevel level, BlockPos pos) {
        if (!LoadedChunks.has(level, pos)) return false;
        var state = level.getBlockState(pos);
        var fluid = state.getFluidState();
        if (!state.is(Blocks.WATER) || !fluid.isSource() || !level.getBlockState(pos.above()).isAir()) return false;
        int neighbors = 0;
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            var p = pos.relative(dir);
            if (!LoadedChunks.has(level, p)) return false;
            var neighbor = level.getBlockState(p);
            if (neighbor.is(Blocks.WATER) && neighbor.getFluidState().isSource()) neighbors++;
        }
        return neighbors >= 2 && fluid.getFlow(level, pos).lengthSqr() < 1.0e-6;
    }

    private Target firstReachable(Villager body, List<Target> candidates) {
        // Bound expensive pathfinding even in large fields or inaccessible lakes.
        long time = body.level().getGameTime();
        unreachableUntil.values().removeIf(expiry -> expiry <= time);
        int attempts = 0;
        for (var candidate : candidates) {
            if (unreachableUntil.containsKey(candidate.stand)) continue;
            if (candidate.task == Task.FISH
                    && !FishingSpots.usable((ServerLevel)body.level(), new FishingSpots.Spot(candidate.pos, candidate.stand))) continue;
            if (++attempts > (farmer ? 24 : 2)) break;
            var found = reachable(body, candidate);
            if (found != null) return found;
            if (unreachableUntil.size() >= 512) unreachableUntil.clear();
            unreachableUntil.put(candidate.stand, time + 600);
        }
        return null;
    }

    private static Target reachable(Villager body, Target target) {
        if (target.task == Task.FISH && !canCastFromBank((ServerLevel)body.level(), body, target)) return null;
        var path = body.getNavigation().createPath(target.stand, target.task == Task.FISH ? 0 : 1);
        return path != null && path.canReach() ? target : null;
    }

    private void forgetFishingSpot(long time) {
        if (target != null) {
            if (unreachableUntil.size() >= 512) unreachableUntil.clear();
            unreachableUntil.put(target.stand, time + 600);
        }
        fishingSpot = null;
    }

    private static boolean canCastFromBank(ServerLevel level, Villager body, Target target) {
        Vec3 eye = Vec3.atBottomCenterOf(target.stand).add(0, body.getEyeHeight(), 0);
        return visibleWater(level, body, eye, target.pos);
    }

    private static boolean visibleWater(ServerLevel level, Villager body, Vec3 eye, BlockPos water) {
        // Aim at the open surface: a ray to the submerged block center can hit the bank.
        Vec3 surface = new Vec3(water.getX() + .5, water.getY() + 1.0, water.getZ() + .5);
        var hit = level.clip(new ClipContext(eye, surface, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, body));
        return hit.getType() == HitResult.Type.MISS;
    }

    private static boolean visible(ServerLevel level, Villager body, BlockPos pos) {
        var hit = level.clip(new ClipContext(body.getEyePosition(), Vec3.atCenterOf(pos), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, body));
        return hit.getType() == HitResult.Type.MISS || hit.getBlockPos().equals(pos);
    }

    private static ItemStack plantingSeed(ServerLevel level, Villager body, BlockPos pos) {
        for (int i = 0; i < body.getInventory().getContainerSize(); i++) {
            var stack = body.getInventory().getItem(i);
            if (!stack.isEmpty() && stack.is(ItemTags.VILLAGER_PLANTABLE_SEEDS)
                    && stack.getItem() instanceof BlockItem item && item.getBlock().defaultBlockState().canSurvive(level, pos))
                return stack.copyWithCount(1);
        }
        return ItemStack.EMPTY;
    }

    private static boolean plant(ServerLevel level, Villager body, BlockPos pos, ItemStack displayedSeeds) {
        if (displayedSeeds.isEmpty() || !level.getBlockState(pos).isAir()
                || !level.getBlockState(pos.below()).is(Blocks.FARMLAND)) return false;
        for (int i = 0; i < body.getInventory().getContainerSize(); i++) {
            var stack = body.getInventory().getItem(i);
            if (!stack.isEmpty() && stack.is(displayedSeeds.getItem()) && stack.is(ItemTags.VILLAGER_PLANTABLE_SEEDS)
                    && stack.getItem() instanceof BlockItem item) {
                BlockState crop = item.getBlock().defaultBlockState();
                if (!crop.canSurvive(level, pos)) continue;
                if (level.setBlockAndUpdate(pos, crop)) {
                    stack.shrink(1);
                    body.getInventory().setChanged();
                    level.playSound(null, pos, SoundEvents.CROP_PLANTED, SoundSource.BLOCKS, 1, 1);
                    return true;
                }
                return false;
            }
        }
        return false;
    }

    public static void store(ServerLevel level, Villager body, ItemStack stack) {
        var leftover = body.getInventory().addItem(stack);
        if (!leftover.isEmpty()) body.spawnAtLocation(level, leftover, .5F);
    }

    private static void finishCompost(ServerLevel level, Villager body) {
        var memory = body.getBrain().getMemory(MemoryModuleType.JOB_SITE);
        if (memory.isEmpty() || !memory.get().dimension().equals(level.dimension())) return;
        var pos = memory.get().pos();
        var state = level.getBlockState(pos);
        if (!state.is(Blocks.COMPOSTER) || state.getValue(ComposterBlock.LEVEL) >= 7) return;
        var inventory = body.getInventory();
        boolean filled = false;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            var stack = inventory.getItem(i);
            if (!stack.is(ItemTags.VILLAGER_PLANTABLE_SEEDS) || !stack.has(DataComponents.COMPOSTABLE)) continue;
            int spare = Math.min(stack.getCount(), inventory.countItem(stack.getItem()) - 10);
            while (spare-- > 0 && state.getValue(ComposterBlock.LEVEL) < 7) {
                var before = state;
                state = ComposterBlock.insertItem(body, state, level, stack, pos);
                filled |= state != before;
            }
        }
        if (filled) level.levelEvent(1500, pos, 1);
        inventory.setChanged();
    }

    private static final class ComposterWork extends WorkAtComposter {
        void perform(ServerLevel level, Villager body, long time) {
            super.start(level, body, time);
            finishCompost(level, body);
        }
    }
    private static final class StationWork extends WorkAtPoi {
        void perform(ServerLevel level, Villager body, long time) { super.start(level, body, time); }
    }
}


