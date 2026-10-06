package naturality.villager;

import com.google.common.collect.ImmutableMap;
import java.lang.ref.WeakReference;
import java.util.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.memory.*;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.villager.*;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;

/** Sheep shearing and shared smith repair assignments, using real mob actions and drops. */
public final class AnimalCareWork extends Behavior<Villager> {
    private record Claim(WeakReference<Villager> worker, long expires) { }
    private static final Map<UUID, Claim> REPAIRS = new HashMap<>();
    private final boolean shepherd;
    private LivingEntity target;
    private ItemStack oldHand = ItemStack.EMPTY, displayed = ItemStack.EMPTY;
    private long nextSearch, nextAction, selectedAt;
    private final List<ItemEntity> drops = new ArrayList<>();

    public AnimalCareWork(boolean shepherd) {
        super(ImmutableMap.of(MemoryModuleType.JOB_SITE, MemoryStatus.VALUE_PRESENT), 1200);
        this.shepherd = shepherd;
    }
    private static boolean available(ServerLevel level, Villager body) {
        return body.isAlive() && !body.isBaby() && !body.isTrading() && !body.isSleeping()
            && body.getBrain().isActive(Activity.WORK)
            && body.getBrain().getMemory(MemoryModuleType.JOB_SITE)
                .filter(site -> site.dimension().equals(level.dimension())).isPresent();
    }
    private static boolean smith(Villager body) {
        var profession = body.getVillagerData().profession();
        return profession.is(VillagerProfession.TOOLSMITH) || profession.is(VillagerProfession.WEAPONSMITH)
            || profession.is(VillagerProfession.ARMORER);
    }
    @Override protected boolean checkExtraStartConditions(ServerLevel level, Villager body) {
        return available(level, body) && (shepherd
            ? body.getVillagerData().profession().is(VillagerProfession.SHEPHERD) : smith(body));
    }
    @Override protected boolean canStillUse(ServerLevel level, Villager body, long time) {
        return checkExtraStartConditions(level, body);
    }
    @Override protected boolean timedOut(long time) { return false; }
    @Override protected void start(ServerLevel level, Villager body, long time) { oldHand = body.getMainHandItem(); }
    private void hold(Villager body, Item item) {
        if (displayed.is(item) && body.getMainHandItem() == displayed) return;
        displayed = new ItemStack(item);
        body.setItemSlot(EquipmentSlot.MAINHAND, displayed);
    }
    private void release(Villager body) {
        if (target instanceof IronGolem) {
            var claim = REPAIRS.get(target.getUUID());
            if (claim != null && claim.worker().get() == body) REPAIRS.remove(target.getUUID());
        }
        target = null;
        if (body.getMainHandItem() == displayed) body.setItemSlot(EquipmentSlot.MAINHAND, oldHand);
        displayed = ItemStack.EMPTY;
        body.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        body.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
        body.getNavigation().stop();
    }
    @Override protected void stop(ServerLevel level, Villager body, long time) {
        release(body); drops.clear();
    }
    private static boolean reachable(Villager body, net.minecraft.world.entity.Entity entity) {
        var path = body.getNavigation().createPath(entity, 1);
        return path != null && path.canReach();
    }
    private boolean approach(Villager body, net.minecraft.world.entity.Entity entity, long time, double reach) {
        body.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new EntityTracker(entity, true));
        if (body.distanceToSqr(entity) <= reach * reach && body.hasLineOfSight(entity)) {
            body.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
            body.getNavigation().stop();
            return true;
        }
        if (time % 10 == 0) body.getBrain().setMemory(MemoryModuleType.WALK_TARGET,
            new WalkTarget(new EntityTracker(entity, false), .55F, 1));
        return false;
    }
    @Override protected void tick(ServerLevel level, Villager body, long time) {
        drops.removeIf(item -> !item.isAlive() || item.getItem().isEmpty()
            || !body.getInventory().canAddItem(item.getItem()));
        if (!drops.isEmpty() && time - selectedAt > 400) { drops.clear(); release(body); }
        if (!drops.isEmpty()) {
            var item = drops.getFirst();
            if (approach(body, item, time, 1.5) && !item.hasPickUpDelay()) {
                var remainder = body.getInventory().addItem(item.getItem().copy());
                int taken = item.getItem().getCount() - remainder.getCount();
                if (taken > 0) {
                    body.take(item, taken);
                    if (remainder.isEmpty()) item.discard(); else item.setItem(remainder);
                    if (!item.isAlive()) { drops.remove(item); if (drops.isEmpty()) release(body); }
                }
            }
            return;
        }
        if (target != null && (!target.isAlive() || time - selectedAt > 600
            || target instanceof Sheep sheep && !sheep.readyForShearing()
            || target instanceof IronGolem golem && golem.getHealth() >= golem.getMaxHealth())) release(body);
        if (target == null) {
            if (time < nextSearch || shepherd && time < nextAction) return;
            nextSearch = time + 40;
            var job = body.getBrain().getMemory(MemoryModuleType.JOB_SITE).orElseThrow().pos();
            var area = new AABB(job).inflate(24);
            if (shepherd) {

                var sheep = level.getEntitiesOfClass(Sheep.class, area, animal -> animal.isAlive() && animal.readyForShearing() && body.getInventory().canAddItem(new ItemStack(Items.WOOL.pick(animal.getColor()))));
                sheep.sort(Comparator.comparingDouble(body::distanceToSqr));
                target = sheep.stream().filter(animal -> reachable(body, animal)).findFirst().orElse(null);
            } else {
                REPAIRS.entrySet().removeIf(entry -> {
                    var worker = entry.getValue().worker().get();
                    return worker == null || !worker.isAlive() || entry.getValue().expires() < time
                        || worker.level() == level && (!available(level, worker) || !smith(worker));
                });
                var golems = level.getEntitiesOfClass(IronGolem.class, area,
                    golem -> golem.isAlive() && golem.getHealth() < golem.getMaxHealth() && !REPAIRS.containsKey(golem.getUUID()));
                golems.sort(Comparator.comparingDouble(body::distanceToSqr));
                for (var golem : golems) {
                    var workers = level.getEntitiesOfClass(Villager.class, golem.getBoundingBox().inflate(24),
                        worker -> available(level, worker) && smith(worker));
                    workers.removeIf(worker -> REPAIRS.values().stream().anyMatch(claim -> claim.worker().get() == worker));
                    workers.sort(Comparator.<Villager>comparingDouble(golem::distanceToSqr).thenComparingInt(Villager::getId));
                    var chosen = workers.stream().filter(worker -> reachable(worker, golem)).findFirst().orElse(null);
                    if (chosen != body) continue;
                    target = golem;
                    REPAIRS.put(golem.getUUID(), new Claim(new WeakReference<>(body), time + 100));
                    break;
                }
            }
            if (target == null) return;
            selectedAt = time;
            hold(body, shepherd ? Items.SHEARS : Items.IRON_INGOT);
            if (!shepherd) nextAction = time + 20;
        }
        if (target instanceof IronGolem)
            REPAIRS.put(target.getUUID(), new Claim(new WeakReference<>(body), time + 100));
        if (!approach(body, target, time, 2.5) || time < nextAction) return;
        body.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT);
        if (target instanceof Sheep sheep) {
            var area = sheep.getBoundingBox().inflate(3);
            var existing = new HashSet<UUID>();
            level.getEntitiesOfClass(ItemEntity.class, area).forEach(item -> existing.add(item.getUUID()));
            sheep.shear(level, SoundSource.NEUTRAL, displayed);
            sheep.gameEvent(GameEvent.SHEAR, body);
            drops.addAll(level.getEntitiesOfClass(ItemEntity.class, area, item -> !existing.contains(item.getUUID())));
            selectedAt = time;
            nextAction = time + 100;
            release(body);
        } else if (target instanceof IronGolem golem) {
            golem.heal(25);
            golem.playSound(SoundEvents.IRON_GOLEM_REPAIR, 1, 1 + (body.getRandom().nextFloat() - body.getRandom().nextFloat()) * .2F);
            // A fresh displayed ingot for each application; supplies are part of the smith's job.
            displayed = ItemStack.EMPTY;
            hold(body, Items.IRON_INGOT);
            nextAction = time + 20;
            if (golem.getHealth() >= golem.getMaxHealth()) release(body);
        }
    }
}
