package naturality.client.mixin;
import naturality.client.confusion.ConfusionSlots;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(AbstractContainerScreen.class)
public abstract class ConfusionContainerMixin {
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
