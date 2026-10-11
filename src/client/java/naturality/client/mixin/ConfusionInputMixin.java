package naturality.client.mixin;
import naturality.client.confusion.ConfusionSlots;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Inventory;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
@Mixin(Minecraft.class)
public abstract class ConfusionInputMixin {
    @WrapOperation(method = "handleKeybinds", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Inventory;setSelectedSlot(I)V"))
    private void naturality$number(Inventory inventory, int cell, Operation<Void> original) {
        original.call(inventory, ConfusionSlots.selectable(cell, inventory.getSelectedSlot()));
    }
}
