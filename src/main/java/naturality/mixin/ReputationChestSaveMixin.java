package naturality.mixin;

import naturality.villager.VillageChest;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.storage.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChestBlockEntity.class)
public abstract class ReputationChestSaveMixin {
    @Inject(method = "saveAdditional", at = @At("TAIL"))
    private void naturality$save(ValueOutput output, CallbackInfo ci) {
        output.putBoolean("NaturalityVillageChest", ((VillageChest)this).naturality$isVillageChest());
    }
    @Inject(method = "loadAdditional", at = @At("TAIL"))
    private void naturality$load(ValueInput input, CallbackInfo ci) {
        ((VillageChest)this).naturality$markVillageChest(input.getBooleanOr("NaturalityVillageChest", false));
    }
}
