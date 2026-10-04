package naturality.villager;

import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;

/** Short-lived farmer work interruption owned by a nearby butcher. */
public final class FarmerSupplies {
    private record Offer(Villager butcher, ItemStack display, long expires) { }
    private static final Map<Villager, Offer> OFFERS = new WeakHashMap<>();
    public static boolean available(Villager farmer, Villager butcher) {
        Offer offer = OFFERS.get(farmer);
        return offer == null || offer.butcher == butcher || offer.expires < farmer.level().getGameTime();
    }
    public static void offer(Villager farmer, Villager butcher, ItemStack display, long time) {
        OFFERS.put(farmer, new Offer(butcher, display, time + 40));
    }
    public static ItemStack display(Villager farmer) {
        Offer offer = OFFERS.get(farmer);
        if (offer == null) return ItemStack.EMPTY;
        if (!offer.butcher.isAlive() || offer.butcher.isTrading()
                || farmer.level().getGameTime() > offer.expires) {
            OFFERS.remove(farmer);
            return ItemStack.EMPTY;
        }
        return offer.display;
    }
    public static void finish(Villager farmer, Villager butcher) {
        Offer offer = OFFERS.get(farmer);
        if (offer != null && offer.butcher == butcher) OFFERS.remove(farmer);
    }
    public static void lookAtRecipient(Villager farmer) {
        Offer offer = OFFERS.get(farmer);
        if (offer == null) return;
        farmer.getBrain().setMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.LOOK_TARGET,
            new net.minecraft.world.entity.ai.behavior.EntityTracker(offer.butcher, true));
        farmer.getBrain().eraseMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.WALK_TARGET);
        farmer.getNavigation().stop();
    }
    private FarmerSupplies() { }
}
