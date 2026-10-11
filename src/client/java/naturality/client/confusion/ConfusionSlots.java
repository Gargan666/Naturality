package naturality.client.confusion;

import java.util.WeakHashMap;
import java.util.Random;
import naturality.NaturalityEffects;
import naturality.client.mixin.ConfusionSlotAccess;
import naturality.client.mixin.ConfusionCreativeSlotAccess;
import net.minecraft.client.Minecraft;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;

public final class ConfusionSlots {
    public static ConfusionLayout hotbar = new ConfusionLayout(9);
    public static ConfusionLayout inventory = new ConfusionLayout(27);
    private static final Random RANDOM = new Random();
    private static final WeakHashMap<Slot, int[]> ORIGINAL = new WeakHashMap<>();
    private static Object owner;
    public static int storageSlot(int cell) { return cell==9?40:storage(cell); }
    public static int selectable(int cell, int current) { return cell==9?current:storage(cell); }
    public static ConfusionLayout.View hud(boolean right) { return null; }
    private static boolean active, returning;
    private static int elapsed;
    private ConfusionSlots() {}
    public static int interval(int amplifier) { return Math.max(5, 40 / (Math.max(0, amplifier) + 1)); }
    public static int maxMovingSlots(int amplifier) { return 5 + 2 * Math.min(15, Math.max(0, amplifier)); }
    public static int[] batch(Random random, int amplifier) {
        // Reserve two movers for the independently synced physical offhand exchange.
        int total = 2 + random.nextInt(maxMovingSlots(amplifier) - 3);
        int hotbarCount;
        do { hotbarCount = random.nextInt(Math.min(9, total) + 1); }
        while (hotbarCount == 1 || total - hotbarCount == 1 || total - hotbarCount > 27);
        return new int[]{hotbarCount, total - hotbarCount};
    }
    public static boolean visible() { return active || returning; }
    public static int visual(int identity) { return hotbar.cell(identity); }
    public static int storage(int cell) { return hotbar.identity(cell); }
    private static void restoreCoordinates() {
        ORIGINAL.forEach((slot, xy) -> { var access = (ConfusionSlotAccess)slot; access.naturality$x(xy[0]); access.naturality$y(xy[1]); });
        ORIGINAL.clear();
    }
    public static void tick(Minecraft client) {
        if (client.player != owner || client.level == null) {
            restoreCoordinates(); owner = client.player; active = returning = false; elapsed = 0;
            hotbar = new ConfusionLayout(9); inventory = new ConfusionLayout(27);
        }
        if (client.player == null || client.isPaused()) return;
        hotbar.tick(); inventory.tick();
        var effect = client.player.getEffect(NaturalityEffects.CONFUSION);
        if (effect != null && client.player.isAlive()) {
            if (!active) { active = true; returning = false; elapsed = 0; }
            int interval = interval(effect.getAmplifier());
            if (++elapsed >= interval) {
                int selectedCell = visual(client.player.getInventory().getSelectedSlot());
                int[] counts = batch(RANDOM, effect.getAmplifier());
                hotbar.swap(RANDOM, counts[0], effect.getAmplifier()); inventory.swap(RANDOM, counts[1], effect.getAmplifier()); elapsed = 0;
                client.player.getInventory().setSelectedSlot(selectable(selectedCell, client.player.getInventory().getSelectedSlot()));
            }
        } else if (active) {
            int selectedCell = visual(client.player.getInventory().getSelectedSlot());
            hotbar.restore(RANDOM); inventory.restore(RANDOM); active = false; returning = true; elapsed = 0;
            client.player.getInventory().setSelectedSlot(selectedCell < 9 ? selectedCell : client.player.getInventory().getSelectedSlot());
        } else if (returning && hotbar.settled() && inventory.settled()) { returning = false; restoreCoordinates(); }
    }
    public static void layout(AbstractContainerMenu menu, float partial) {
        if (!visible()) return;
        var client = Minecraft.getInstance();
        if (client.player == null) return;
        Slot[] slots = new Slot[36];
        for (Slot slot : menu.slots) {
            if (slot.container != client.player.getInventory()) continue;
            int index = slot instanceof ConfusionCreativeSlotAccess wrapper
                ? wrapper.naturality$target().getContainerSlot() : slot.getContainerSlot();
            if(index<0 || index>=36) continue;
            slots[index]=slot; ORIGINAL.computeIfAbsent(slot,s->new int[]{s.x,s.y});
        }
        for(int i=0;i<slots.length;i++) {
            Slot slot=slots[i]; if(slot==null) continue;
            boolean hands=i<9;
            int identity=hands?i:i-9;
            Slot first=slots[hands?0:9]; if(first==null) continue;
            int[] origin=ORIGINAL.get(first);
            var access=(ConfusionSlotAccess)slot;
            var layout=hands?hotbar:inventory;
            access.naturality$x(Math.round(origin[0]+layout.coordinate(identity,partial,9,false,18)));
            access.naturality$y(Math.round(origin[1]+layout.coordinate(identity,partial,9,true,18)));
        }
    }
}