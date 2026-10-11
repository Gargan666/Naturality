package naturality.client.mixin;
import naturality.client.confusion.ConfusionSlots;
import net.minecraft.client.MouseHandler;
import net.minecraft.world.entity.player.Inventory;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
@Mixin(MouseHandler.class)
public abstract class ConfusionScrollMixin {
    @WrapOperation(method = "onScroll", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Inventory;getSelectedSlot()I"))
    private int naturality$visual(Inventory inventory, Operation<Integer> original) { return ConfusionSlots.visual(original.call(inventory)); }
    @WrapOperation(method = "onScroll", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Inventory;setSelectedSlot(I)V"))
    private void naturality$storage(Inventory inventory,int cell,Operation<Void> original) {
        original.call(inventory,ConfusionSlots.selectable(cell,inventory.getSelectedSlot()));
    }
}