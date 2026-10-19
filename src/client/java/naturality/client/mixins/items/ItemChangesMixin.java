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
        @org.spongepowered.asm.mixin.Unique
        private final naturality.client.ui.items.HotbarPickupScale.Animation naturality$hotbarAnimation =
            new naturality.client.ui.items.HotbarPickupScale.Animation();

        @com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod(method = "extractHotbarAndDecorations")
        private void naturality$raisedHotbar(GuiGraphicsExtractor graphics, DeltaTracker delta, Operation<Void> original) {
            graphics.pose().pushMatrix();
            try {
                graphics.pose().translate(0, -naturality.config.live.NaturalityConfig.get().effects.hotbarOffset);
                original.call(graphics, delta);
            } finally { graphics.pose().popMatrix(); }
        }

        @com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod(method = "extractItemHotbar")
        private void naturality$updateHotbarAnimation(GuiGraphicsExtractor graphics, DeltaTracker delta, Operation<Void> original) {
            var player = net.minecraft.client.Minecraft.getInstance().player;
            if (player != null) naturality$hotbarAnimation.update(player, delta.getGameTimeDeltaPartialTick(false));
            original.call(graphics, delta);
        }

        @WrapOperation(method = "extractSlot", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;item(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;III)V"))
        private void naturality$selectedSprite(GuiGraphicsExtractor graphics, net.minecraft.world.entity.LivingEntity owner,
            ItemStack stack, int x, int y, int seed, Operation<Void> original) {
            float scale = naturality$hotbarAnimation.scale(seed);
            graphics.pose().pushMatrix();
            try {
                graphics.pose().translate(x + 8, y + 8);
                graphics.pose().scale(scale, scale);
                graphics.pose().translate(-(x + 8), -(y + 8));
                original.call(graphics, owner, stack, x, y, seed);
            } finally { graphics.pose().popMatrix(); }
        }
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
