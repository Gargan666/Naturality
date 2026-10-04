package naturality.client.mixin;

import naturality.villager.VillagerWorkVisuals;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.properties.conditional.FishingRodCast;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FishingRodCast.class)
public abstract class VillagerFishingRodCastMixin {
    @Inject(method = "get", at = @At("HEAD"), cancellable = true)
    private void naturality$castModel(ItemStack stack, @Nullable ClientLevel level,
            @Nullable LivingEntity owner, int seed, ItemDisplayContext context,
            CallbackInfoReturnable<Boolean> cir) {
        if (owner instanceof Villager villager && villager.getMainHandItem() == stack)
            cir.setReturnValue(((VillagerWorkVisuals)villager).naturality$castTarget() != null);
    }
}
