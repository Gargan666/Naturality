package naturality.confusion;

import naturality.NaturalityEffects;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** Server-owned exchanges; restoration moves a surviving stack, never creates one. */
public final class ConfusionOffhand {
    private boolean active;
    private int elapsed, lastSlot = -1;
    private ItemStack original = ItemStack.EMPTY, reference = ItemStack.EMPTY;
    public void tick(ServerPlayer player) {
        var effect = player.getEffect(NaturalityEffects.CONFUSION);
        if (effect == null || !player.isAlive()) { restore(player); return; }
        if (!active) {
            active = true; elapsed = 0; lastSlot = -1;
            reference = player.getOffhandItem(); original = reference.copy();
        }
        int interval = Math.max(5, 40 / (effect.getAmplifier() + 1));
        if (++elapsed >= interval) {
            elapsed = 0;
            if (player.getRandom().nextInt(4) == 0) exchange(player, player.getRandom().nextInt(9));
        }
    }
    public void exchange(ServerPlayer player, int slot) {
        if (!active) throw new IllegalStateException("No remembered offhand");
        if (slot < 0 || slot >= 9) throw new IllegalArgumentException("Hotbar slot");
        var inventory = player.getInventory();
        ItemStack hand = inventory.getItem(40), other = inventory.getItem(slot);
        if (hand.isEmpty() && other.isEmpty()) return;
        inventory.setItem(40, other); inventory.setItem(slot, hand); lastSlot = slot;
        sync(player);
    }
    private static void sync(ServerPlayer player) {
        player.getInventory().setChanged();
        player.inventoryMenu.broadcastChanges();
        if (player.containerMenu != player.inventoryMenu) player.containerMenu.broadcastChanges();
    }
    public void restore(ServerPlayer player) {
        if (!active) return;
        var inventory = player.getInventory();
        int found = -1;
        if (original.isEmpty()) {
            if (!player.getOffhandItem().isEmpty()) {
                if (lastSlot >= 0 && inventory.getItem(lastSlot).isEmpty()) found = lastSlot;
                if (found < 0) for (int i = 0; i < 36; i++) if (inventory.getItem(i).isEmpty()) { found = i; break; }
            }
        } else {
            // Object identity follows damage/count changes while the actual stack survives.
            for (int i = 0; i < 41; i++) if ((i < 36 || i == 40) && !reference.isEmpty() && inventory.getItem(i) == reference) { found = i; break; }
            if (found < 0 && !reference.isEmpty() && player.containerMenu.getCarried() == reference) found = 41;
            // Menu dragging and save/reload may replace stack objects. Only use an
            // unambiguous matching survivor; a saved snapshot is never restored as an item.
            if (found < 0) {
                int candidates = 0;
                for (int i = 0; i < 42; i++) {
                    if(i >= 36 && i < 40) continue;
                    ItemStack stack = i == 41 ? player.containerMenu.getCarried() : inventory.getItem(i);
                    if (!stack.isEmpty() && ItemStack.isSameItemSameComponents(stack, original)) { found = i; candidates++; }
                }
                if (candidates != 1) found = -1;
            }
        }
        if (found >= 0 && found != 40) {
            ItemStack hand = inventory.getItem(40);
            ItemStack survivor = found == 41 ? player.containerMenu.getCarried() : inventory.getItem(found);
            inventory.setItem(40, survivor);
            if (found == 41) player.containerMenu.setCarried(hand); else inventory.setItem(found, hand);
            sync(player);
        }
        active = false; elapsed = 0; lastSlot = -1; original = reference = ItemStack.EMPTY;
    }
    public void save(ValueOutput output) {
        output.putBoolean("NaturalityConfusionOffhandActive", active);
        if (!active) return;
        output.putInt("NaturalityConfusionOffhandLastSlot", lastSlot);
        output.putInt("NaturalityConfusionOffhandElapsed", elapsed);
        if (!original.isEmpty()) output.store("NaturalityConfusionOriginalOffhand", ItemStack.CODEC,
            reference.isEmpty() ? original : reference);
    }
    public void load(ValueInput input) {
        active = input.getBooleanOr("NaturalityConfusionOffhandActive", false);
        original = input.read("NaturalityConfusionOriginalOffhand", ItemStack.CODEC).orElse(ItemStack.EMPTY);
        reference = ItemStack.EMPTY;
        lastSlot = input.getIntOr("NaturalityConfusionOffhandLastSlot", -1);
        elapsed = input.getIntOr("NaturalityConfusionOffhandElapsed", 0);
    }
}
