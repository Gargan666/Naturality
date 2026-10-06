package naturality.mixin;

import naturality.villager.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.village.ReputationEventType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.villager.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(Villager.class)
public abstract class ReputationVillagerMixin {
    @Inject(method = "customServerAiStep", at = @At("HEAD"), cancellable = true)
    private void naturality$sleepDebt(ServerLevel level, CallbackInfo ci) {
        if (VillagerSleep.tick(level, (Villager)(Object)this)) ci.cancel();
    }
    @Shadow private void updateSpecialPrices(Player player) { throw new AssertionError(); }
    @Unique private int naturality$priceSignature = -1;
    @Unique private static int naturality$signature(Player player) {
        var hero = player.getEffect(MobEffects.HERO_OF_THE_VILLAGE);
        return Reputation.score(player) + (hero == null ? 0 : 1000 + 100 * hero.getAmplifier());
    }
    @Inject(method = "customServerAiStep", at = @At("TAIL"))
    private void naturality$refreshPrices(ServerLevel level, CallbackInfo ci) {
        var trader = ((Villager)(Object)this).getTradingPlayer();
        if (trader != null && naturality$signature(trader) != naturality$priceSignature) updateSpecialPrices(trader);
    }
    @Inject(method = "mobInteract", at = @At("HEAD"), cancellable = true)
    private void naturality$interact(Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        var body = (Villager)(Object)this;
        if (!(body.level() instanceof ServerLevel level) || !body.isAlive() || body.isBaby() || body.isSleeping() || body.isTrading()) return;
        if (body.getVillagerData().profession().is(VillagerProfession.NITWIT)) {
            Reputation.nitwit(level, body, player); cir.setReturnValue(InteractionResult.SUCCESS_SERVER);
        } else if (Reputation.tradeBlocked(body, player)) {
            body.setUnhappyCounter(40); body.makeSound(SoundEvents.VILLAGER_NO);
            cir.setReturnValue(InteractionResult.SUCCESS_SERVER);
        }
    }
    @Inject(method = "updateSpecialPrices", at = @At("HEAD"), cancellable = true)
    private void naturality$prices(Player player, CallbackInfo ci) {
        var body = (Villager)(Object)this;
        var hero = player.getEffect(MobEffects.HERO_OF_THE_VILLAGE);
        double discount = hero == null ? 0 : .3F + .0625F * hero.getAmplifier();
        var offers = body.getOffers();
        for (var offer : offers) {
            offer.resetSpecialPriceDiff();
            offer.addToSpecialPriceDiff(Reputation.priceDiff(offer.getBaseCostA().getCount(), Reputation.score(player)));
            if (discount > 0) offer.addToSpecialPriceDiff(-Math.max(1, (int)Math.floor(discount * offer.getBaseCostA().getCount())));
        }
        var trader = body.getTradingPlayer();
        if (trader != null && !offers.isEmpty()) {
            if (trader.containerMenu instanceof MerchantMenu menu) menu.updateSellItem();
            trader.sendMerchantOffers(trader.containerMenu.containerId, offers, body.getVillagerData().level(),
                body.getVillagerXp(), body.showProgressBar(), body.canRestock());
        }
        ci.cancel();
    }
    @Inject(method = "rewardTradeXp", at = @At("TAIL"))
    private void naturality$trade(MerchantOffer offer, CallbackInfo ci) {
        var body = (Villager)(Object)this;
        if (body.getTradingPlayer() != null) {

            if (body.getTradingPlayer() != null) updateSpecialPrices(body.getTradingPlayer());
        }
    }
    @Inject(method = "onReputationEventFrom", at = @At("HEAD"))
    private void naturality$cure(ReputationEventType type, Entity source, CallbackInfo ci) {
        var body = (Villager)(Object)this;
        if (type == ReputationEventType.ZOMBIE_VILLAGER_CURED && source instanceof Player player
                && body.level() instanceof ServerLevel level
                && ReputationData.get(level.getServer()).firstCure(Reputation.state(body).lineage)) Reputation.change(player, 10);
    }
    @Inject(method = "getPlayerReputation", at = @At("HEAD"), cancellable = true)
    private void naturality$gossipReplaced(Player player, CallbackInfoReturnable<Integer> cir) {
        // Vanilla village defenders must not attack at the trade-refusal threshold.
        cir.setReturnValue(Reputation.score(player) == 0 ? -100 : 0);
    }
    @Inject(method = "wantsToPickUp", at = @At("HEAD"), cancellable = true)
    private void naturality$wantGift(ServerLevel level, ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        var body = (Villager)(Object)this;
        if (Reputation.gift(body, stack)) cir.setReturnValue(stack.is(net.minecraft.world.item.Items.FISHING_ROD)
            || body.getInventory().canAddItem(stack));
    }
    @Inject(method = "pickUpItem", at = @At("HEAD"), cancellable = true)
    private void naturality$gift(ServerLevel level, ItemEntity entity, CallbackInfo ci) {
        if (Reputation.acceptGift(level, (Villager)(Object)this, entity)) ci.cancel();
    }
    @Inject(method = "handleEntityEvent", at = @At("HEAD"), cancellable = true)
    private void naturality$nod(byte event, CallbackInfo ci) {
        if (event == Reputation.NOD_EVENT) {
            var body = (Villager)(Object)this;
            Reputation.state(body).nodStart = body.tickCount; ci.cancel();
        }
    }
}
