package naturality.client.mixin;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(CreativeModeInventoryScreen.class)
public interface ConfusionCreativeScreenAccess {
    @Accessor("hasClickedOutside") void naturality$outside(boolean outside);
}
