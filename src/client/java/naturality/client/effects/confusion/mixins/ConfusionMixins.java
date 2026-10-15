package naturality.client.effects.confusion.mixins;

import naturality.client.effects.confusion.ConfusionSlots;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.DeltaTracker;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.injection.At;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mutable;

/** Feature integration hooks; each nested type retains its independent Mixin target and gate. */
public final class ConfusionMixins {
    private ConfusionMixins() {}

    @Mixin(AbstractContainerScreen.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class ConfusionContainerMixin {
        @Shadow private Slot getHoveredSlot(double x, double y) { throw new AssertionError(); }
        @WrapOperation(method = {"mouseClicked", "mouseReleased"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/inventory/AbstractContainerScreen;hasClickedOutside(DDII)Z"))
        private boolean naturality$movingHit(AbstractContainerScreen<?> screen, double x, double y, int left, int top, Operation<Boolean> original) {
            boolean outside = original.call(screen, x, y, left, top);
            if (ConfusionSlots.visible() && getHoveredSlot(x, y) != null) {
                if (screen instanceof ConfusionCreativeScreenAccess creative) creative.naturality$outside(false);
                return false;
            }
            return outside;
        }
        @ModifyArg(method = {"checkHotbarKeyPressed", "checkHotbarMouseClicked"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/inventory/AbstractContainerScreen;slotClicked(Lnet/minecraft/world/inventory/Slot;IILnet/minecraft/world/inventory/ContainerInput;)V"), index = 2)
        private int naturality$swapKey(int cell) { return cell >= 0 && cell < 9 ? ConfusionSlots.storageSlot(cell) : cell; }
        @SuppressWarnings("null") @Shadow @Final protected AbstractContainerMenu menu;
        @Inject(method = "extractContents", at = @At("HEAD"))
        private void naturality$layout(GuiGraphicsExtractor graphics, int x, int y, float partial, CallbackInfo ci) {
            ConfusionSlots.layout(menu, partial);
        }
    }

    @Mixin(CreativeModeInventoryScreen.class)
    @org.jspecify.annotations.NullMarked
    public interface ConfusionCreativeScreenAccess {
        @Accessor("hasClickedOutside") void naturality$outside(boolean outside);
    }

    @Mixin(targets = "net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen$SlotWrapper")
    @org.jspecify.annotations.NullMarked
    public interface ConfusionCreativeSlotAccess {
        @Accessor("target") Slot naturality$target();
    }

    @Mixin(Hud.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class ConfusionHudMixin {
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
                targetX+=naturality.client.effects.confusion.ConfusionSlots.OffhandTransfer.offset(storage,partial,false);
                targetY+=naturality.client.effects.confusion.ConfusionSlots.OffhandTransfer.offset(storage,partial,true);
                graphics.pose().translate(graphics.guiWidth()/2-88+targetX-x,graphics.guiHeight()-19+targetY-y);
                original.call(hud, graphics, x, y, delta, player, stack, seed);
                graphics.pose().popMatrix();
            } else original.call(hud, graphics, x, y, delta, player, stack, seed);
        }
    }

    @Mixin(Minecraft.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class ConfusionInputMixin {
        @WrapOperation(method = "handleKeybinds", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Inventory;setSelectedSlot(I)V"))
        private void naturality$number(Inventory inventory, int cell, Operation<Void> original) {
            original.call(inventory, ConfusionSlots.selectable(cell, inventory.getSelectedSlot()));
        }
    }

    @Mixin(MouseHandler.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class ConfusionScrollMixin {
        @WrapOperation(method = "onScroll", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Inventory;getSelectedSlot()I"))
        private int naturality$visual(Inventory inventory, Operation<Integer> original) { return ConfusionSlots.visual(original.call(inventory)); }
        @WrapOperation(method = "onScroll", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Inventory;setSelectedSlot(I)V"))
        private void naturality$storage(Inventory inventory,int cell,Operation<Void> original) {
            original.call(inventory,ConfusionSlots.selectable(cell,inventory.getSelectedSlot()));
        }
    }

    @Mixin(Slot.class)
    @org.jspecify.annotations.NullMarked
    public interface ConfusionSlotAccess {
        @Mutable @Accessor("x") void naturality$x(int value);
        @Mutable @Accessor("y") void naturality$y(int value);
    }
}
