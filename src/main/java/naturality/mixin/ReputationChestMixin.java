package naturality.mixin;

import naturality.villager.VillageChest;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.storage.loot.LootTable;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RandomizableContainerBlockEntity.class)
public abstract class ReputationChestMixin implements VillageChest {
    @Unique private boolean naturality$villageChest;
    @Shadow protected ResourceKey<LootTable> lootTable;
    @Override public boolean naturality$isVillageChest() {
        if (lootTable != null && lootTable.identifier().getPath().startsWith("chests/village/")) naturality$villageChest = true;
        return naturality$villageChest;
    }
    @Unique public void naturality$markVillageChest(boolean marked) { naturality$villageChest |= marked; }
    @Inject(method = "setLootTable", at = @At("HEAD"))
    private void naturality$provenance(ResourceKey<LootTable> loot, CallbackInfo ci) {
        naturality$isVillageChest();
        if (loot != null && loot.identifier().getPath().startsWith("chests/village/")) naturality$villageChest = true;
    }
}
