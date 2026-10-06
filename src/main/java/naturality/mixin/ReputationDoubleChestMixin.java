package naturality.mixin;

import naturality.villager.VillageChest;
import net.minecraft.world.CompoundContainer;
import net.minecraft.world.Container;
import org.spongepowered.asm.mixin.*;

@Mixin(CompoundContainer.class)
public abstract class ReputationDoubleChestMixin implements VillageChest {
    @Shadow @Final private Container container1;
    @Shadow @Final private Container container2;
    @Override public boolean naturality$isVillageSlot(int slot) {
        var container = slot < container1.getContainerSize() ? container1 : container2;
        return container instanceof VillageChest chest && chest.naturality$isVillageChest();
    }
    @Override public boolean naturality$isVillageChest() {
        return container1 instanceof VillageChest first && first.naturality$isVillageChest()
            || container2 instanceof VillageChest second && second.naturality$isVillageChest();
    }
}
