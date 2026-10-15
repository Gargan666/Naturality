package naturality.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3x2fStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Hud.class)
public abstract class HotbarPickupScaleMixin {
    @WrapOperation(method = "extractSlot", at = @At(value = "INVOKE", target = "Lorg/joml/Matrix3x2fStack;scale(FF)Lorg/joml/Matrix3x2f;"))
    private org.joml.Matrix3x2f naturality$uniformPop(Matrix3x2fStack pose, float xScale, float yScale,
                                                    Operation<org.joml.Matrix3x2f> original) {
        return original.call(pose, yScale, yScale);
    }

    @WrapOperation(method = "extractSlot", at = @At(value = "INVOKE", target = "Lorg/joml/Matrix3x2fStack;translate(FF)Lorg/joml/Matrix3x2f;"))
    private org.joml.Matrix3x2f naturality$centerPop(Matrix3x2fStack pose, float offsetX, float offsetY,
        Operation<org.joml.Matrix3x2f> original, GuiGraphicsExtractor graphics, int x, int y,
        DeltaTracker delta, Player player, ItemStack stack, int seed) {
        if (offsetY == y + 12) offsetY = y + 8;
        else if (offsetY == -(y + 12)) offsetY = -(y + 8);
        return original.call(pose, offsetX, offsetY);
    }
}
