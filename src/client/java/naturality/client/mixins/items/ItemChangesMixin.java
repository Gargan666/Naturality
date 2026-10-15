package naturality.client.mixins.items;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Hud;
import net.minecraft.world.entity.player.Player;
import org.joml.Matrix3x2fStack;

/** Feature integration hooks; each nested type retains its independent Mixin target and gate. */
public final class ItemChangesMixin {
    private ItemChangesMixin() {}

    @Mixin(AbstractContainerScreen.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class CarriedItemScaleMixin {
        @WrapOperation(method = "extractFloatingItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;item(Lnet/minecraft/world/item/ItemStack;II)V"))
        private void naturality$largerCursorItem(GuiGraphicsExtractor graphics, ItemStack stack, int x, int y,
                                                Operation<Void> original) {
            naturality.client.ui.items.CarriedItemScale.draw(graphics, stack, x, y, original);
        }
    }

    @Mixin(Hud.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class HotbarPickupScaleMixin {
        @WrapOperation(method = "extractSlot", at = @At(value = "INVOKE", target = "Lorg/joml/Matrix3x2fStack;scale(FF)Lorg/joml/Matrix3x2f;"))
        private org.joml.Matrix3x2f naturality$uniformPop(Matrix3x2fStack pose, float xScale, float yScale,
                                                        Operation<org.joml.Matrix3x2f> original) {
            float scale = naturality.client.ui.items.HotbarPickupScale.uniformScale(yScale);
            return original.call(pose, scale, scale);
        }

        @WrapOperation(method = "extractSlot", at = @At(value = "INVOKE", target = "Lorg/joml/Matrix3x2fStack;translate(FF)Lorg/joml/Matrix3x2f;"))
        private org.joml.Matrix3x2f naturality$centerPop(Matrix3x2fStack pose, float offsetX, float offsetY,
            Operation<org.joml.Matrix3x2f> original, GuiGraphicsExtractor graphics, int x, int y,
            DeltaTracker delta, Player player, ItemStack stack, int seed) {
            offsetY = naturality.client.ui.items.HotbarPickupScale.centeredOffset(offsetY, y);
            return original.call(pose, offsetX, offsetY);
        }
    }
}
