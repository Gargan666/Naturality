package naturality.mixin;

import naturality.villager.VillagerBread;
import naturality.villager.VillagerBreadState;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Villager.class)
public abstract class VillagerBreadMixin implements VillagerBreadState {
    @Unique private final VillagerBread naturality$bread = new VillagerBread();
    @Override public VillagerBread naturality$bread() { return naturality$bread; }
    @Inject(method = "customServerAiStep", at = @At("TAIL"))
    private void naturality$evening(ServerLevel level, CallbackInfo ci) {
        naturality$bread.tick(level, (Villager)(Object)this, level.getGameTime());
    }
    @Inject(method = "addAdditionalSaveData", at = @At("HEAD"))
    private void naturality$beforeSaveBread(ValueOutput output, CallbackInfo ci) {
        naturality$bread.beforeSave((Villager)(Object)this);
    }
    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void naturality$saveBread(ValueOutput output, CallbackInfo ci) {
        naturality$bread.save(output);
        naturality$bread.afterSave((Villager)(Object)this);
    }
    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void naturality$loadBread(ValueInput input, CallbackInfo ci) { naturality$bread.load(input); }
    @Inject(method = "wantsToPickUp", at = @At("HEAD"), cancellable = true)
    private void naturality$acceptBread(ServerLevel level, ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (stack.is(Items.BREAD)) cir.setReturnValue(((Villager)(Object)this).getInventory().canAddItem(stack));
    }
}
