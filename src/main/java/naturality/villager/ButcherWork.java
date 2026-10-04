package naturality.villager;

import com.google.common.collect.ImmutableMap;
import java.util.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.*;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.villager.*;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.*;
import net.minecraft.world.phys.Vec3;

/** Breeds one pair, waits for its child, harvests one parent, and restocks at a farmer. */
public final class ButcherWork extends Behavior<Villager> {
    private enum Phase { SEARCH, FEED, BIRTH, WAIT, HUNT, LOOT, SUPPLIES, HOME }
    private static final Item[] FOODS = {Items.WHEAT, Items.CARROT, Items.WHEAT_SEEDS};
    public static boolean isLivestock(Animal animal) {
        return animal.getType() == EntityTypes.COW || animal.getType() == EntityTypes.SHEEP
            || animal.getType() == EntityTypes.PIG || animal.getType() == EntityTypes.CHICKEN;
    }
    private static final Map<UUID, ButcherWork> CLAIMS = new HashMap<>();
    private Phase phase = Phase.SEARCH;
    private Animal first, second;
    private Item food;
    private int fed, deliveries;
    private long since, nextAction, birthTime = -1;
    private Villager farmer;
    private ItemEntity supplyGift;
    private ItemStack oldHand = ItemStack.EMPTY, tool = ItemStack.EMPTY;
    private final List<ItemEntity> drops = new ArrayList<>();
    private final List<Vec3> harvestSpots = new ArrayList<>();
    private final Set<UUID> ignoredDrops = new HashSet<>();

    public ButcherWork() { super(ImmutableMap.of(MemoryModuleType.JOB_SITE, MemoryStatus.VALUE_PRESENT), 1200); }
    @Override public String debugString() { return "ButcherWork[" + phase + ", fed=" + fed + ", birth=" + birthTime + "]"; }
    @Override protected boolean checkExtraStartConditions(ServerLevel level, Villager body) {
        return !body.isBaby() && !body.isTrading() && body.getBrain().isActive(Activity.WORK)
            && body.getVillagerData().profession().is(VillagerProfession.BUTCHER)
            && body.getBrain().getMemory(MemoryModuleType.JOB_SITE)
                .filter(site -> site.dimension().equals(level.dimension())).isPresent();
    }
    // Finish an active breeding/harvest cycle before the brain renews this behavior.
    @Override protected boolean timedOut(long time) {
        return (phase == Phase.SEARCH || phase == Phase.HOME) && super.timedOut(time);
    }
    @Override protected boolean canStillUse(ServerLevel level, Villager body, long time) {
        return checkExtraStartConditions(level, body);
    }
    @Override protected void start(ServerLevel level, Villager body, long time) {
        oldHand = body.getMainHandItem();
        phase = Phase.SEARCH;
        since = time;
    }
    @Override protected void stop(ServerLevel level, Villager body, long time) {
        release();
        if (farmer != null) FarmerSupplies.finish(farmer, body);
        farmer = null;
        supplyGift = null;
        equip(body, ItemStack.EMPTY);
        body.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        body.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
        body.getNavigation().stop();
    }
    private void equip(Villager body, ItemStack item) {
        if (!tool.isEmpty() && body.getMainHandItem() == tool) body.setItemSlot(EquipmentSlot.MAINHAND, oldHand);
        tool = item;
        if (!item.isEmpty()) body.setItemSlot(EquipmentSlot.MAINHAND, item);
    }
    private void phase(Phase value, long time) { phase = value; since = time; nextAction = time; }
    private void release() {
        if (first != null) CLAIMS.remove(first.getUUID(), this);
        if (second != null) CLAIMS.remove(second.getUUID(), this);
        first = second = null;
        birthTime = -1;
    }
    public static void born(Animal parent, Animal partner, long time) {
        ButcherWork work = CLAIMS.get(parent.getUUID());
        if (work != null && CLAIMS.get(partner.getUUID()) == work
                && (work.first == parent && work.second == partner || work.first == partner && work.second == parent))
            work.birthTime = time;
    }
    private boolean approach(Villager body, net.minecraft.world.entity.Entity target, long time, double reach) {
        body.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new EntityTracker(target, true));
        if (body.distanceToSqr(target) <= reach * reach && body.hasLineOfSight(target)) {
            body.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
            body.getNavigation().stop();
            return true;
        }
        if (time % 10 == 0) body.getBrain().setMemory(MemoryModuleType.WALK_TARGET,
            new WalkTarget(new EntityTracker(target, false), phase == Phase.HUNT ? .85F : .55F, 1));
        return false;
    }
    @Override protected void tick(ServerLevel level, Villager body, long time) {
        if (time - since > 600) {
            if (farmer != null) FarmerSupplies.finish(farmer, body);
            farmer = null;
            supplyGift = null;
            release();
            phase(Phase.HOME, time);
        }
        switch (phase) {
            case SEARCH -> {
                if (time < nextAction || !body.onGround()) return;
                nextAction = time + 60;
                var job = body.getBrain().getMemory(MemoryModuleType.JOB_SITE).orElseThrow().pos();
                List<Animal> animals = level.getEntitiesOfClass(Animal.class,
                    new net.minecraft.world.phys.AABB(job).inflate(24),
                    a -> isLivestock(a) && a.isAlive() && !a.isBaby() && a.getAge() == 0 && a.canFallInLove()
                        && !a.isInLove() && !CLAIMS.containsKey(a.getUUID()));
                animals.sort(Comparator.comparingDouble(body::distanceToSqr));
                // Animals are distance-sorted; food never decides species priority.
                for (Animal a : animals) {
                    Item candidate = a.getType() == EntityTypes.PIG ? Items.CARROT
                        : a.getType() == EntityTypes.CHICKEN ? Items.WHEAT_SEEDS : Items.WHEAT;
                    for (Animal b : animals) {
                        if (a == b || a.getType() != b.getType() || a.distanceToSqr(b) > 256
                                || !a.isFood(new ItemStack(candidate)) || !b.isFood(new ItemStack(candidate))) continue;
                        var path = body.getNavigation().createPath(a, 2);
                        var partnerPath = body.getNavigation().createPath(b, 2);
                        if (path == null || !path.canReach() || partnerPath == null || !partnerPath.canReach()) continue;
                        first = a; second = b; food = candidate; fed = 0;
                        CLAIMS.put(a.getUUID(), this); CLAIMS.put(b.getUUID(), this);
                        if (body.getInventory().countItem(food) < 2) { deliveries = 0; phase(Phase.SUPPLIES, time); return; }
                        phase(Phase.FEED, time);
                        equip(body, new ItemStack(food));
                        return;
                    }
                }
                body.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
                body.getNavigation().stop();
            }
            case FEED -> {
                Animal target = fed == 0 ? first : second;
                if (target == null || !target.isAlive() || target.isBaby() || target.getAge() != 0) {
                    release(); phase(Phase.HOME, time); return;
                }
                if (!approach(body, target, time, 2.5) || time < nextAction) return;
                if (body.getInventory().removeItemType(food, 1).isEmpty()) {
                    release(); phase(Phase.HOME, time); return;
                }
                target.setInLove(null);
                body.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT);
                fed++;
                nextAction = time + 10;
                if (fed == 2) { equip(body, ItemStack.EMPTY); phase(Phase.BIRTH, time); }
            }
            case BIRTH -> {
                if (!first.isAlive() || !second.isAlive()) { release(); phase(Phase.HOME, time); return; }
                if (birthTime >= 0) phase(Phase.WAIT, time);
                else {
                    approach(body, first, time, 4);
                    if (first.distanceToSqr(second) > 36 && time % 10 == 0) {
                        first.getNavigation().moveTo(second, 1);
                        second.getNavigation().moveTo(first, 1);
                    }
                }
            }
            case WAIT -> {
                if (time >= birthTime + 20) {
                    phase(Phase.HUNT, time);
                    harvestSpots.clear(); ignoredDrops.clear(); drops.clear();
                    level.getEntitiesOfClass(ItemEntity.class, body.getBoundingBox().inflate(40))
                        .forEach(item -> ignoredDrops.add(item.getUUID()));
                    equip(body, new ItemStack(Items.IRON_SWORD));
                }
            }
            case HUNT -> {
                collectDrops(level);
                Animal target = first.isAlive() ? first : null;
                if (target == null) { release(); equip(body, ItemStack.EMPTY); phase(Phase.LOOT, time); return; }
                if (target.isBaby()) { release(); phase(Phase.HOME, time); return; }
                if (!approach(body, target, time, 2.6) || time < nextAction) return;
                nextAction = time + 20;
                Set<UUID> existing = new HashSet<>();
                var area = target.getBoundingBox().inflate(3);
                level.getEntitiesOfClass(ItemEntity.class, area).forEach(item -> existing.add(item.getUUID()));
                body.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT);
                target.hurtServer(level, body.damageSources().mobAttack(body), 6);
                if (!target.isAlive()) harvestSpots.add(target.position());
                if (!target.isAlive()) drops.addAll(level.getEntitiesOfClass(ItemEntity.class, area,
                    item -> !existing.contains(item.getUUID())));
            }
            case LOOT -> {
                collectDrops(level);
                drops.removeIf(item -> !item.isAlive() || item.getItem().isEmpty());
                if (drops.isEmpty() && time - since > 40 || time - since > 200) { drops.clear(); phase(Phase.HOME, time); return; }
                if (drops.isEmpty()) return;
                ItemEntity item = drops.getFirst();
                if (!approach(body, item, time, 1.5) || item.hasPickUpDelay()) return;
                ItemStack remaining = body.getInventory().addItem(item.getItem().copy());
                if (remaining.getCount() < item.getItem().getCount()) {
                    body.take(item, item.getItem().getCount() - remaining.getCount());
                    if (remaining.isEmpty()) item.discard(); else item.setItem(remaining);
                }
            }
            case SUPPLIES -> supplies(level, body, time);
            case HOME -> {
                equip(body, ItemStack.EMPTY);
                var job = body.getBrain().getMemory(MemoryModuleType.JOB_SITE).orElseThrow().pos();
                body.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(job));
                if (job.closerToCenterThan(body.position(), 2.5)) { phase(Phase.SEARCH, time); return; }
                if (time % 20 == 0) body.getBrain().setMemory(MemoryModuleType.WALK_TARGET,
                    new WalkTarget(job, .5F, 2));
            }
        }
    }
    private void collectDrops(ServerLevel level) {
        for (Vec3 pos : harvestSpots) {
            var area = new net.minecraft.world.phys.AABB(pos, pos).inflate(3);
            for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, area))
                if (!ignoredDrops.contains(item.getUUID()) && !drops.contains(item)) drops.add(item);
        }
    }
    private void supplies(ServerLevel level, Villager body, long time) {
        if (deliveries == 0 && (first == null || second == null || !first.isAlive() || !second.isAlive()
                || first.isBaby() || second.isBaby() || first.getAge() != 0 || second.getAge() != 0)) {
            if (farmer != null) FarmerSupplies.finish(farmer, body);
            farmer = null;
            if (supplyGift != null) supplyGift.discard();
            supplyGift = null;
            release(); phase(Phase.HOME, time); return;
        }
        if (farmer != null && (!farmer.isAlive() || farmer.isTrading()
                || !farmer.getBrain().isActive(Activity.WORK)
                || !farmer.getVillagerData().profession().is(VillagerProfession.FARMER))) {
            FarmerSupplies.finish(farmer, body); farmer = null; supplyGift = null;
        }
        if (farmer == null) {
            if (time < nextAction) return;
            nextAction = time + 60;
            farmer = level.getEntitiesOfClass(Villager.class, body.getBoundingBox().inflate(48),
                v -> !v.isBaby() && !v.isTrading() && v.getBrain().isActive(Activity.WORK)
                    && v.getVillagerData().profession().is(VillagerProfession.FARMER)
                    && FarmerSupplies.available(v, body))
                .stream().sorted(Comparator.comparingDouble(body::distanceToSqr))
                .filter(v -> { var path = body.getNavigation().createPath(v, 1); return path != null && path.canReach(); })
                .findFirst().orElse(null);
            deliveries = 0;
        }
        if (farmer == null || !approach(body, farmer, time, 2)) return;
        if (!FarmerSupplies.available(farmer, body)) { farmer = null; return; }
        if (deliveries >= 3) {
            FarmerSupplies.finish(farmer, body); farmer = null; release(); phase(Phase.HOME, time); return;
        }
        FarmerSupplies.offer(farmer, body, new ItemStack(FOODS[deliveries], 8), time);
        farmer.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new EntityTracker(body, true));
        farmer.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        farmer.getNavigation().stop();
        if (supplyGift != null) {
            if (!supplyGift.isAlive()) { supplyGift = null; nextAction = time + 20; return; }
            if (body.distanceToSqr(supplyGift) > 2.25) return;
            ItemStack remainder = body.getInventory().addItem(supplyGift.getItem().copy());
            int received = supplyGift.getItem().getCount() - remainder.getCount();
            if (received > 0) body.take(supplyGift, received);
            if (!remainder.isEmpty()) { supplyGift.setItem(remainder); return; }
            supplyGift.discard(); supplyGift = null; deliveries++; nextAction = time + 20;
            return;
        }
        if (time < nextAction) return;
        nextAction = time + 20;
        farmer.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT);
        var gift = new ItemEntity(level, farmer.getX(), farmer.getEyeY() - .3, farmer.getZ(), new ItemStack(FOODS[deliveries], 8));
        gift.setDeltaMovement(body.position().subtract(farmer.position()).normalize().scale(.25).add(0, .12, 0));
        // The controller collects its reserved delivery; keep vanilla nearby
        // villagers from taking it first and causing duplicate replenishment.
        gift.setPickUpDelay(200);
        level.addFreshEntity(gift);
        supplyGift = gift;
    }
}
