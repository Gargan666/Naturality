package naturality.client.ui.inventory.mixins;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import naturality.client.ui.inventory.TrashSlotRendering;
import naturality.ui.inventory.TrashSlot;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.jspecify.annotations.NullMarked;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@NullMarked
public final class InventoryMixins {
    private InventoryMixins() {}

    @Mixin(AbstractContainerScreen.class)
    public static abstract class Screens {
        @Shadow public abstract AbstractContainerMenu getMenu();

        @Inject(method = "<init>", at = @At("TAIL"))
        private void naturality$attachTrash(CallbackInfo ci) {
            TrashSlot.attach(getMenu());
        }

        @Inject(method = "extractContents", at = @At(value = "INVOKE", target =
                "Lnet/minecraft/client/gui/screens/inventory/AbstractContainerScreen;extractLabels(Lnet/minecraft/client/gui/GuiGraphicsExtractor;II)V"))
        private void naturality$trashPanel(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
            // The pose already includes the screen origin and any custom screen scaling.
            TrashSlotRendering.background(graphics, getMenu());
        }

        @WrapOperation(method = {"mouseClicked", "mouseReleased"}, at = @At(value = "INVOKE", target =
                "Lnet/minecraft/client/gui/screens/inventory/AbstractContainerScreen;hasClickedOutside(DDII)Z"))
        private boolean naturality$includeTrash(AbstractContainerScreen<?> screen, double mouseX, double mouseY,
                int left, int top, Operation<Boolean> original) {
            // Wrap the virtual call so recipe-book, loom and custom screen overrides also participate.
            return original.call(screen, mouseX, mouseY, left, top)
                    && !TrashSlotRendering.contains(getMenu(), mouseX, mouseY, left, top);
        }
    }
}
