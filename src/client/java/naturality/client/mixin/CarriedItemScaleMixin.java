package naturality.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(AbstractContainerScreen.class)
public abstract class CarriedItemScaleMixin {
    @WrapOperation(method = "extractFloatingItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;item(Lnet/minecraft/world/item/ItemStack;II)V"))
    private void naturality$largerCursorItem(GuiGraphicsExtractor graphics, ItemStack stack, int x, int y,
                                            Operation<Void> original) {
        graphics.pose().pushMatrix();
        try {
            graphics.pose().translate(x + 8, y + 8);
            graphics.pose().scale(1.2F, 1.2F);
            graphics.pose().translate(-(x + 8), -(y + 8));
            original.call(graphics, stack, x, y);
        } finally { graphics.pose().popMatrix(); }
    }
}
