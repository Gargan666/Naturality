package naturality.mixin;

import naturality.villager.VillagerWorkVisuals;
import net.minecraft.world.entity.ai.behavior.ShowTradesToPlayer;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ShowTradesToPlayer.class)
public abstract class VillagerTradeDisplayMixin {
    @Inject(method = "displayAsHeldItem", at = @At("TAIL"))
    private static void naturality$display(Villager body, ItemStack stack, CallbackInfo ci) {
        ((VillagerWorkVisuals)body).naturality$setDisplayingTrade(true);
    }
    @Inject(method = "clearHeldItem", at = @At("TAIL"))
    private static void naturality$clear(Villager body, CallbackInfo ci) {
        ((VillagerWorkVisuals)body).naturality$setDisplayingTrade(false);
    }
}
