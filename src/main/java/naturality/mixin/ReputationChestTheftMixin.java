package naturality.mixin;

import java.util.HashMap;
import java.util.Map;
import naturality.villager.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContainerMenu.class)
public abstract class ReputationChestTheftMixin {
    @Unique private final Map<Slot, ItemStack> naturality$before = new HashMap<>();
    @Inject(method = "clicked", at = @At("HEAD"))
    private void naturality$before(int slot, int button, ContainerInput input, Player player, CallbackInfo ci) {
        naturality$before.clear();
        if (!(player.level() instanceof ServerLevel)) return;
        for (var entry : ((AbstractContainerMenu)(Object)this).slots)
            if (entry.container instanceof VillageChest chest && chest.naturality$isVillageSlot(entry.getContainerSlot()))
                naturality$before.put(entry, entry.getItem().copy());
    }
    @Inject(method = "clicked", at = @At("RETURN"))
    private void naturality$after(int slot, int button, ContainerInput input, Player player, CallbackInfo ci) {
        boolean stolen = naturality$before.entrySet().stream().anyMatch(entry -> {
            var old = entry.getValue(); var now = entry.getKey().getItem();
            return !old.isEmpty() && (!ItemStack.isSameItemSameComponents(old, now) || now.getCount() < old.getCount());
        });
        naturality$before.clear();
        if (stolen) Reputation.change(player, -3);
    }
}
