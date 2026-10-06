package naturality.mixin;

import java.util.Objects;
import java.util.UUID;
import naturality.villager.*;
import net.minecraft.core.UUIDUtil;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemEntity.class)
public abstract class ReputationGiftDonorMixin implements GiftDonor {
    @Unique private UUID naturality$donor;
    @Override public UUID naturality$giftDonor() {
        if (naturality$donor == null && ((ItemEntity)(Object)this).getOwner() instanceof Player player) naturality$donor = player.getUUID();
        return naturality$donor;
    }
    @Inject(method = "setThrower", at = @At("TAIL"))
    private void naturality$thrower(Entity entity, CallbackInfo ci) {
        naturality$donor = entity instanceof Player ? entity.getUUID() : null;
    }
    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void naturality$save(ValueOutput output, CallbackInfo ci) { output.storeNullable("NaturalityGiftDonor", UUIDUtil.CODEC, naturality$giftDonor()); }
    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void naturality$load(ValueInput input, CallbackInfo ci) { naturality$donor = input.read("NaturalityGiftDonor", UUIDUtil.CODEC).orElse(null); }
    @Inject(method = "tryToMerge", at = @At("HEAD"), cancellable = true)
    private void naturality$keepDonors(ItemEntity other, CallbackInfo ci) {
        var stack = ((ItemEntity)(Object)this).getItem();
        if ((stack.is(Items.EMERALD) || stack.is(Items.FISHING_ROD) || Reputation.crop(stack))
            && !Objects.equals(naturality$giftDonor(), ((GiftDonor)other).naturality$giftDonor())) ci.cancel();
    }
}
