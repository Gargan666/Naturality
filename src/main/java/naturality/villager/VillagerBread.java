package naturality.villager;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** Per-villager evening sharing and optional meals; durable day limits survive reloads. */
public final class VillagerBread {
    private long workedDay = -1, eatenDay = -1;
    private long nextMeal, nextShare, heldSince;
    private Villager recipient;
    private boolean eating;
    private ItemStack displayed = ItemStack.EMPTY, previousHand = ItemStack.EMPTY;
    private boolean savingDisplay;
    private final Map<UUID, Long> sharedUntil = new HashMap<>();

    public long eatenDay() { return eatenDay; }
    public long workedDay() { return workedDay; }
    public void beforeSave(Villager body) {
        savingDisplay = !displayed.isEmpty() && body.getMainHandItem() == displayed;
        if (savingDisplay) body.setItemSlot(EquipmentSlot.MAINHAND, previousHand);
    }
    public void afterSave(Villager body) {
        if (savingDisplay) body.setItemSlot(EquipmentSlot.MAINHAND, displayed);
        savingDisplay = false;
    }
    public void save(ValueOutput output) {
        output.putLong("NaturalityFarmerWorkedDay", workedDay);
        output.putLong("NaturalityFarmerBreadDay", eatenDay);
    }
    public void load(ValueInput input) {
        workedDay = input.getLongOr("NaturalityFarmerWorkedDay", -1);
        eatenDay = input.getLongOr("NaturalityFarmerBreadDay", -1);
    }
    private static boolean farmer(Villager body) {
        return body.getVillagerData().profession().is(VillagerProfession.FARMER);
    }
    private static boolean social(Villager body) {
        var brain = body.getBrain();
        return body.isAlive() && !body.isSleeping() && !body.isTrading()
            && !((VillagerWorkVisuals)body).naturality$isDisplayingTrade()
            && (brain.isActive(Activity.IDLE) || brain.isActive(Activity.MEET) || brain.isActive(Activity.PLAY) || brain.isActive(Activity.REST));
    }
    private void hold(Villager body, long time) {
        previousHand = body.getMainHandItem();
        displayed = new ItemStack(Items.BREAD);
        body.setItemSlot(EquipmentSlot.MAINHAND, displayed);
        heldSince = time;
    }
    private void clear(Villager body) {
        if (eating) body.stopUsingItem();
        if (!displayed.isEmpty() && (body.getMainHandItem() == displayed || body.getMainHandItem().isEmpty()))
            body.setItemSlot(EquipmentSlot.MAINHAND, previousHand);
        // Vanilla consumption can shrink the displayed stack to zero.
        else if (eating && body.getMainHandItem().isEmpty()) body.setItemSlot(EquipmentSlot.MAINHAND, previousHand);
        displayed = ItemStack.EMPTY;
        eating = false;
        if (recipient != null) {
            body.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
            body.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
            body.getNavigation().stop();
        }
        recipient = null;
    }
    public void tick(ServerLevel level, Villager body, long time) {
        long day = Math.floorDiv(level.getOverworldClockTime(), 24000);
        long hour = Math.floorMod(level.getOverworldClockTime(), 24000);
        boolean isFarmer = farmer(body);
        if (isFarmer && !body.isBaby() && body.getBrain().isActive(Activity.WORK) && hour >= 2000 && hour < 9000)
            workedDay = day;
        boolean afterShift = workedDay == day && hour >= 9000;
        if (!social(body) || isFarmer && !afterShift) { clear(body); return; }
        if (eating) {
            if (time - heldSince >= 32) {
                if (!body.getInventory().removeItemType(Items.BREAD, 1).isEmpty()) eatenDay = day;
                clear(body);
                nextMeal = time + 600 + body.getRandom().nextInt(601);
            }
            return;
        }
        if (recipient != null) {
            if (VillagerSnowShelter.snowing(level, body) || !recipient.isAlive() || recipient.isSleeping() || time - heldSince > 400
                    || body.getInventory().countItem(Items.BREAD) <= (eatenDay == day ? 0 : 1)) {
                clear(body); nextShare = time + 100; return;
            }
            body.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new EntityTracker(recipient, true));
            if (body.distanceToSqr(recipient) > 9 || !body.hasLineOfSight(recipient)) {
                if (time % 10 == 0) body.getBrain().setMemory(MemoryModuleType.WALK_TARGET,
                    new WalkTarget(new EntityTracker(recipient, false), .5F, 2));
                return;
            }
            body.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
            body.getNavigation().stop();
            if (time - heldSince < 20) return;
            ItemStack bread = body.getInventory().removeItemType(Items.BREAD, 1);
            if (!bread.isEmpty()) {
                var gift = new ItemEntity(level, body.getX(), body.getEyeY() - .3, body.getZ(), bread);
                gift.setDeltaMovement(recipient.position().subtract(body.position()).normalize().scale(.3).add(0, .15, 0));
                gift.setDefaultPickUpDelay();
                level.addFreshEntity(gift);
                body.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT);
                sharedUntil.put(recipient.getUUID(), time + 1200);
            }
            clear(body); nextShare = time + 100;
            return;
        }
        if (!body.getMainHandItem().isEmpty()) return;
        if (isFarmer && time >= nextShare && hour < 12000 && !VillagerSnowShelter.snowing(level, body)) {
            sharedUntil.entrySet().removeIf(entry -> entry.getValue() <= time);
            var home = body.getBrain().getMemory(MemoryModuleType.JOB_SITE)
                .filter(site -> site.dimension().equals(level.dimension())).map(site -> site.pos()).orElse(body.blockPosition());
            var villagers = level.getEntitiesOfClass(Villager.class, new net.minecraft.world.phys.AABB(home).inflate(32),
                other -> other != body && other.isAlive() && !other.isSleeping() && !other.isTrading()
                    && !sharedUntil.containsKey(other.getUUID()) && other.getInventory().countItem(Items.BREAD) < 3
                    && other.getInventory().canAddItem(new ItemStack(Items.BREAD)));
            villagers.sort(Comparator.<Villager>comparingInt(other -> other.getInventory().countItem(Items.BREAD))
                .thenComparingDouble(body::distanceToSqr));
            if (body.getInventory().countItem(Items.BREAD) < 2) {
                int amount = Math.min(villagers.size() + 1, body.getInventory().countItem(Items.WHEAT) / 3);
                if (amount > 0) {
                    body.getInventory().removeItemType(Items.WHEAT, amount * 3);
                    ProfessionWork.store(level, body, new ItemStack(Items.BREAD, amount));
                }
            }
            if (body.getInventory().countItem(Items.BREAD) > (eatenDay == day ? 0 : 1)) {
                recipient = villagers.stream().filter(other -> {
                    var path = body.getNavigation().createPath(other, 2);
                    return path != null && path.canReach();
                }).findFirst().orElse(null);
                if (recipient != null) { hold(body, time); return; }
            }
            nextShare = time + 100;
        }
        if (time >= nextMeal && time % 20 == 0 && body.getInventory().countItem(Items.BREAD) > 0
                && (!isFarmer || eatenDay != day) && body.getRandom().nextInt(20) == 0) {
            hold(body, time);
            eating = true;
            body.startUsingItem(InteractionHand.MAIN_HAND);
        }
    }
}
