package naturality.client.mixin;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(Slot.class)
public interface ConfusionSlotAccess {
    @Mutable @Accessor("x") void naturality$x(int value);
    @Mutable @Accessor("y") void naturality$y(int value);
}
