package naturality.client.confusion;

import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;

/** Animates real server exchanges without changing their new storage locations. */
public final class OffhandTransfer {
    private static Object owner;
    private static ItemStack previousHand=ItemStack.EMPTY;
    private static final ItemStack[] previous=new ItemStack[9];
    private static ConfusionLayout motion;
    private static ConfusionLayout.View hud;
    private static int target=-1;
    private static float handX, slotX, slotY;
    private static final Random RANDOM=new Random();
    private OffhandTransfer() {}
    public static void tick(Minecraft client) {
        var player = client.player;
        if(player!=owner || client.level==null) {
            owner=player; previousHand=ItemStack.EMPTY; motion=null; target=-1;
            java.util.Arrays.fill(previous,ItemStack.EMPTY);
            if(player==null) return;
            remember(client); return;
        }
        if(player==null || client.isPaused()) return;
        if(motion!=null) { motion.tick(); if(motion.settled()) motion=null; }
        ItemStack hand=player.getOffhandItem();
        if(ConfusionSlots.visible() && !ItemStack.matches(hand,previousHand)) {
            for(int i=0;i<9;i++) {
                ItemStack stack=player.getInventory().getItem(i);
                if(ItemStack.matches(stack,previousHand) && ItemStack.matches(hand,previous[i])
                    && !ItemStack.matches(stack,previous[i])) {
                    target=i;
                    handX=player.getMainArm()==net.minecraft.world.entity.HumanoidArm.LEFT?9.45F:-1.45F;
                    slotX=ConfusionSlots.hotbar.coordinate(i,0,9,false,1);
                    slotY=ConfusionSlots.hotbar.coordinate(i,0,9,true,1);
                    motion=new ConfusionLayout(2);
                    hud=motion.view(new float[]{handX,slotX},new float[]{0,slotY},true);
                    var effect=player.getEffect(naturality.NaturalityEffects.CONFUSION);
                    motion.swap(RANDOM,2,effect==null?0:effect.getAmplifier());
                    break;
                }
            }
        }
        remember(client);
    }
    private static void remember(Minecraft client) {
        var player = client.player; if (player == null) return;
        previousHand=player.getOffhandItem().copy();
        for(int i=0;i<9;i++) previous[i]=player.getInventory().getItem(i).copy();
    }
    public static float offset(int inventorySlot,float partial,boolean vertical) {
        if(motion==null || (inventorySlot!=40 && inventorySlot!=target)) return 0;
        boolean hand=inventorySlot==40;
        float endpoint=vertical?(hand?0:slotY):(hand?handX:slotX);
        return (hud.coordinate(hand?1:0,partial,vertical,1)-endpoint)*20;
    }
}
