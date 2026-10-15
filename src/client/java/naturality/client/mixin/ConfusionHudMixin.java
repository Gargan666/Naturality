package naturality.client.mixin;
import naturality.client.confusion.ConfusionSlots;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.DeltaTracker;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
@Mixin(Hud.class)
public abstract class ConfusionHudMixin {
    @WrapOperation(method="extractItemHotbar",at=@At(value="INVOKE",target="Lnet/minecraft/world/item/ItemStack;isEmpty()Z"))
    private boolean naturality$offhandVisible(ItemStack stack, Operation<Boolean> original) {
        return ConfusionSlots.visible()?false:original.call(stack);
    }
    @WrapOperation(method="extractItemHotbar",at=@At(value="INVOKE",target="Lnet/minecraft/client/gui/GuiGraphicsExtractor;blitSprite(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIII)V"))
    private void naturality$frame(GuiGraphicsExtractor graphics, com.mojang.renderpearl.api.pipeline.RenderPipeline pipeline, net.minecraft.resources.Identifier sprite, int x,int y,int width,int height,Operation<Void> original) {
        String path=sprite.getPath();
        if(ConfusionSlots.visible() && path.contains("hotbar_offhand")) {
            var player=net.minecraft.client.Minecraft.getInstance().player;
            if(player != null && !player.getInventory().getItem(ConfusionSlots.storageSlot(9)).isEmpty())
                original.call(graphics,pipeline,sprite,x,y,width,height);
            return;
        }
        if(ConfusionSlots.visible() && path.contains("hotbar_selection")) {
            var client=net.minecraft.client.Minecraft.getInstance();
            var player = client.player;
            if (player == null) { original.call(graphics,pipeline,sprite,x,y,width,height); return; }
            boolean right=player.getMainArm()==net.minecraft.world.entity.HumanoidArm.LEFT;
            int cell=ConfusionSlots.visual(player.getInventory().getSelectedSlot());
            float target=cell==9?(right?189:-29):cell*20;
            float dx=graphics.guiWidth()/2-92+target-x, dy=0;
            graphics.pose().pushMatrix(); graphics.pose().translate(dx,dy);
            original.call(graphics,pipeline,sprite,x,y,width,height); graphics.pose().popMatrix();
        } else original.call(graphics,pipeline,sprite,x,y,width,height);
    }
    @WrapOperation(method = "extractItemHotbar", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Inventory;getSelectedSlot()I"))
    private int naturality$selection(Inventory inventory, Operation<Integer> original) {
        return ConfusionSlots.visual(original.call(inventory));
    }
    @WrapOperation(method = "extractItemHotbar", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Hud;extractSlot(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IILnet/minecraft/client/DeltaTracker;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/ItemStack;I)V"))
    private void naturality$motion(Hud hud, GuiGraphicsExtractor graphics, int x, int y, DeltaTracker delta, Player player, ItemStack stack, int seed, Operation<Void> original) {
        if (seed <= 10 && ConfusionSlots.visible()) {
            float partial = delta.getGameTimeDeltaPartialTick(false);
            int i = seed - 1;
            graphics.pose().pushMatrix();
            boolean right=player.getMainArm()==net.minecraft.world.entity.HumanoidArm.LEFT;
            float targetX=i==9?(right?189:-29):ConfusionSlots.hotbar.coordinate(i,partial,9,false,20);
            float targetY=i==9?0:ConfusionSlots.hotbar.coordinate(i,partial,9,true,20);
            int storage=i==9?40:i;
            targetX+=naturality.client.confusion.OffhandTransfer.offset(storage,partial,false);
            targetY+=naturality.client.confusion.OffhandTransfer.offset(storage,partial,true);
            graphics.pose().translate(graphics.guiWidth()/2-88+targetX-x,graphics.guiHeight()-19+targetY-y);
            original.call(hud, graphics, x, y, delta, player, stack, seed);
            graphics.pose().popMatrix();
        } else original.call(hud, graphics, x, y, delta, player, stack, seed);
    }
}
