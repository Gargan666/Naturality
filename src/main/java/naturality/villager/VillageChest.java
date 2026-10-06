package naturality.villager;

public interface VillageChest {
    boolean naturality$isVillageChest();
    default boolean naturality$isVillageSlot(int slot) { return naturality$isVillageChest(); }
    default void naturality$markVillageChest(boolean marked) { }
}