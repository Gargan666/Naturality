package naturality.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerData;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Villager.class)
public abstract class VillagerSuppliesMixin {
    @Unique private boolean naturality$pendingFarmSupplies;
    @Unique private boolean naturality$butcherSupplied;
    @Unique private boolean naturality$loading;

    @Unique private void naturality$supplyButcher() {
        var body = (Villager)(Object)this;
        if (!naturality$loading && !naturality$butcherSupplied
                && body.getVillagerData().profession().is(VillagerProfession.BUTCHER)) {
            naturality$butcherSupplied = true;
            for (var item : new net.minecraft.world.item.Item[]{Items.WHEAT, Items.CARROT, Items.WHEAT_SEEDS})
                body.getInventory().addItem(new ItemStack(item, 4));
        }
    }

    @Unique private void naturality$supplyFarmer() {
        var body = (Villager)(Object)this;
        if (naturality$pendingFarmSupplies && body.getVillagerData().profession().is(VillagerProfession.FARMER)) {
            naturality$pendingFarmSupplies = false;
            body.getInventory().addItem(new ItemStack(Items.BONE_MEAL, 15));
        }
    }

    @Inject(method = "finalizeSpawn", at = @At("TAIL"))
    private void naturality$spawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason,
            SpawnGroupData data, CallbackInfoReturnable<SpawnGroupData> cir) {
        naturality$supplyButcher();
        if (reason == EntitySpawnReason.STRUCTURE) {
            naturality$pendingFarmSupplies = true;
            naturality$supplyFarmer();
        }
    }

    @Inject(method = "setVillagerData", at = @At("TAIL"))
    private void naturality$profession(VillagerData data, CallbackInfo ci) { naturality$supplyFarmer(); naturality$supplyButcher(); }

    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void naturality$save(ValueOutput output, CallbackInfo ci) {
        output.putBoolean("NaturalityPendingFarmSupplies", naturality$pendingFarmSupplies);
        output.putBoolean("NaturalityButcherSupplied", naturality$butcherSupplied);
    }

    @Inject(method = "readAdditionalSaveData", at = @At("HEAD"))
    private void naturality$beginLoad(ValueInput input, CallbackInfo ci) { naturality$loading = true; }

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void naturality$load(ValueInput input, CallbackInfo ci) {
        naturality$pendingFarmSupplies = input.getBooleanOr("NaturalityPendingFarmSupplies", false);
        naturality$supplyFarmer();
        naturality$butcherSupplied = input.getBooleanOr("NaturalityButcherSupplied", false);
        naturality$loading = false;
        naturality$supplyButcher();
    }

    @Inject(method = "wantsToPickUp", at = @At("HEAD"), cancellable = true)
    private void naturality$pickUp(ServerLevel level, ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        var body = (Villager)(Object)this;
        if (stack.is(Items.BONE_MEAL) && body.getVillagerData().profession().is(VillagerProfession.FARMER))
            cir.setReturnValue(body.getInventory().canAddItem(stack));
    }
}
