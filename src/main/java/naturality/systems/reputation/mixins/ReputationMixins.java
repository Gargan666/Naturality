package naturality.systems.reputation.mixins;

import naturality.systems.reputation.Reputation;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractBedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import naturality.systems.reputation.Reputation.VillageChest;
import net.minecraft.server.level.*;
import net.minecraft.world.Container;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.storage.loot.LootTable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.tags.ItemTags;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.golem.IronGolem;
import org.spongepowered.asm.mixin.Unique;
import net.minecraft.world.CompoundContainer;
import naturality.systems.reputation.Reputation.GiftDonor;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Items;
import naturality.systems.reputation.Reputation.GossipReactions;
import naturality.systems.reputation.Reputation.ReputationHolder;
import naturality.systems.reputation.Reputation.ReputationState;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.monster.zombie.ZombieVillager;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import java.util.Set;
import net.minecraft.world.entity.raid.Raid;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import naturality.systems.villagerwork.sleep.VillagerSleep;
import naturality.systems.reputation.Reputation.ReputationData;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.village.ReputationEventType;
import net.minecraft.world.entity.npc.villager.*;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.trading.MerchantOffer;

/** Feature integration hooks; each nested type retains its independent Mixin target and gate. */
public final class ReputationMixins {
    private ReputationMixins() {}

    @Mixin(AbstractBedBlock.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class ReputationBedMixin {
        @Inject(method = "useWithoutItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/AbstractBedBlock;kickVillagerOutOfBed(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)Z"))
        private void naturality$wake(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit,
                CallbackInfoReturnable<InteractionResult> cir) {
            var villagers = level.getEntitiesOfClass(Villager.class, new AABB(pos), Villager::isSleeping);
            if (!villagers.isEmpty()) Reputation.wake(villagers.getFirst(), player);
        }
    }

    @Mixin(ServerPlayerGameMode.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class ReputationChestBreakMixin {
        @SuppressWarnings("null") @Shadow protected ServerLevel level;
        @SuppressWarnings("null") @Shadow @Final protected ServerPlayer player;
        @Unique private boolean naturality$breakingVillageLoot;
        @Inject(method = "destroyBlock", at = @At("HEAD"))
        private void naturality$before(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
            var entity = level.getBlockEntity(pos);
            naturality$breakingVillageLoot = !player.isCreative() && entity instanceof VillageChest chest
                && chest.naturality$isVillageChest() && entity instanceof Container contents && !contents.isEmpty();
        }
        @Inject(method = "destroyBlock", at = @At("RETURN"))
        private void naturality$after(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
            if (naturality$breakingVillageLoot && cir.getReturnValueZ()) Reputation.change(player, -3);
            naturality$breakingVillageLoot = false;
        }
    }

    @Mixin(RandomizableContainerBlockEntity.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class ReputationChestMixin implements VillageChest {
        @Unique private boolean naturality$villageChest;
        @Shadow protected @org.jspecify.annotations.Nullable ResourceKey<LootTable> lootTable;
        @Override public boolean naturality$isVillageChest() {
            var loot = lootTable;
            if (loot != null && loot.identifier().getPath().startsWith("chests/village/")) naturality$villageChest = true;
            return naturality$villageChest;
        }
        @Unique public void naturality$markVillageChest(boolean marked) { naturality$villageChest |= marked; }
        @Inject(method = "setLootTable", at = @At("HEAD"))
        private void naturality$provenance(ResourceKey<LootTable> loot, CallbackInfo ci) {
            naturality$isVillageChest();
            if (loot != null && loot.identifier().getPath().startsWith("chests/village/")) naturality$villageChest = true;
        }
    }

    @Mixin(ChestBlockEntity.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class ReputationChestSaveMixin {
        @Inject(method = "saveAdditional", at = @At("TAIL"))
        private void naturality$save(ValueOutput output, CallbackInfo ci) {
            output.putBoolean("NaturalityVillageChest", ((VillageChest)this).naturality$isVillageChest());
        }
        @Inject(method = "loadAdditional", at = @At("TAIL"))
        private void naturality$load(ValueInput input, CallbackInfo ci) {
            ((VillageChest)this).naturality$markVillageChest(input.getBooleanOr("NaturalityVillageChest", false));
        }
    }

    @Mixin(AbstractContainerMenu.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class ReputationChestTheftMixin {
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

    @Mixin(LivingEntity.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class ReputationDamageMixin {
        @Unique private boolean naturality$wasAsleep;
        @Inject(method = "hurtServer", at = @At("HEAD"))
        private void naturality$beforeHit(ServerLevel level, DamageSource source, float damage, CallbackInfoReturnable<Boolean> cir) {
            naturality$wasAsleep = (Object)this instanceof Villager body && body.isSleeping();
        }
        @Inject(method = "hurtServer", at = @At("RETURN"))
        private void naturality$hit(ServerLevel level, DamageSource source, float damage, CallbackInfoReturnable<Boolean> cir) {
            if (cir.getReturnValueZ() && damage > 0 && (Object)this instanceof Villager body && body.isAlive()
                    && source.getEntity() instanceof Player player) {
                var item = player.getMainHandItem();
                boolean weapon = item.is(ItemTags.SWORDS) || item.has(DataComponents.WEAPON) || item.has(DataComponents.TOOL)
                    || item.is(net.minecraft.world.item.Items.BOW) || item.is(net.minecraft.world.item.Items.CROSSBOW)
                    || item.is(net.minecraft.world.item.Items.TRIDENT);
                Reputation.change(player, weapon ? -5 : -2, body.position());
                if (naturality$wasAsleep && !body.isSleeping()) Reputation.forceWoken(body, player);
            }
        }
        @Inject(method = "die", at = @At("HEAD"))
        private void naturality$death(DamageSource source, CallbackInfo ci) {
            var body = (LivingEntity)(Object)this;
            if (body.level() instanceof ServerLevel level && source.getEntity() instanceof Player player) {
                if (body instanceof Villager) {
                    Reputation.set(player, Reputation.score(player) <= 20 ? 0 : 20);
                    Reputation.upset(level, body.position());
                }
                else if (body instanceof IronGolem) Reputation.change(player, -10, body.position());
            }
        }
    }

    @Mixin(CompoundContainer.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class ReputationDoubleChestMixin implements VillageChest {
        @SuppressWarnings("null") @Shadow @Final private Container container1;
        @SuppressWarnings("null") @Shadow @Final private Container container2;
        @Override public boolean naturality$isVillageSlot(int slot) {
            var container = slot < container1.getContainerSize() ? container1 : container2;
            return container instanceof VillageChest chest && chest.naturality$isVillageChest();
        }
        @Override public boolean naturality$isVillageChest() {
            return container1 instanceof VillageChest first && first.naturality$isVillageChest()
                || container2 instanceof VillageChest second && second.naturality$isVillageChest();
        }
    }

    @Mixin(ItemEntity.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class ReputationGiftDonorMixin implements GiftDonor {
        @Unique private @org.jspecify.annotations.Nullable UUID naturality$donor;
        @Override public @org.jspecify.annotations.Nullable UUID naturality$giftDonor() {
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

    @Mixin(Mob.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class ReputationMobMixin implements ReputationHolder {
        @Unique private final ReputationState naturality$rep = new ReputationState();
        @Override public ReputationState naturality$reputationState() { return naturality$rep; }
        @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
        private void naturality$save(ValueOutput output, CallbackInfo ci) {
            if ((Object)this instanceof Villager || (Object)this instanceof ZombieVillager) naturality$rep.save(output);
        }
        @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
        private void naturality$load(ValueInput input, CallbackInfo ci) {
            if ((Object)this instanceof Villager || (Object)this instanceof ZombieVillager) naturality$rep.load(input);
        }
        @WrapOperation(method = "convertTo(Lnet/minecraft/world/entity/EntityType;Lnet/minecraft/world/entity/ConversionParams;Lnet/minecraft/world/entity/EntitySpawnReason;Lnet/minecraft/world/entity/ConversionParams$AfterConversion;)Lnet/minecraft/world/entity/Mob;",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/ConversionParams$AfterConversion;finalizeConversion(Lnet/minecraft/world/entity/Mob;)V"))
        private void naturality$lineage(ConversionParams.AfterConversion<Mob> callback, Mob converted,
                Operation<Void> original) {
            if (((Object)this instanceof Villager || (Object)this instanceof ZombieVillager)
                    && (converted instanceof Villager || converted instanceof ZombieVillager))
                Reputation.state(converted).copyFrom(naturality$rep);
            original.call(callback, converted);
        }
        @Inject(method = "canAttack(Lnet/minecraft/world/entity/LivingEntity;)Z", at = @At("HEAD"), cancellable = true)
        private void naturality$allies(LivingEntity target, CallbackInfoReturnable<Boolean> cir) {
            if (target instanceof Player player && Reputation.illagerAlly((Mob)(Object)this, player)) cir.setReturnValue(false);
        }
        @Inject(method = "playAmbientSound", at = @At("HEAD"))
        private void naturality$conversationBob(CallbackInfo ci) {
            var mob = (Mob)(Object)this;
            if (mob instanceof Villager body && mob.level() instanceof ServerLevel level) GossipReactions.speaking(level, body);
        }
        @Inject(method = "serverAiStep", at = @At("HEAD"))
        private void naturality$fearBeforeWork(CallbackInfo ci) {
            var mob = (Mob)(Object)this;
            if (mob instanceof Villager) Reputation.react((ServerLevel)mob.level(), mob);
        }
        @Inject(method = "serverAiStep", at = @At("TAIL"))
        private void naturality$react(CallbackInfo ci) {
            var mob = (Mob)(Object)this;
            Reputation.react((ServerLevel)mob.level(), mob);
        }
    }

    @Mixin(Raid.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class ReputationRaidMixin {
        @SuppressWarnings("null") @Shadow @Final private Set<UUID> heroesOfTheVillage;
        @Unique private boolean naturality$wasVictory;

        @Inject(method = "tick", at = @At("HEAD"))
        private void naturality$beforeRaid(ServerLevel level, CallbackInfo ci) {
            naturality$wasVictory = ((Raid)(Object)this).isVictory();
        }

        @Inject(method = "tick", at = @At("RETURN"))
        private void naturality$raidVictory(ServerLevel level, CallbackInfo ci) {
            if (!naturality$wasVictory && ((Raid)(Object)this).isVictory())
                for (var hero : heroesOfTheVillage) Reputation.credit(level, hero, 20);
        }
    }

    @Mixin(Villager.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class ReputationVillagerMixin {
        @Inject(method = "customServerAiStep", at = @At("HEAD"), cancellable = true)
        private void naturality$sleepDebt(ServerLevel level, CallbackInfo ci) {
            var body = (Villager)(Object)this;
            if (VillagerSleep.tick(level, body)) {
                var state = Reputation.state(body);
                state.sleepCoreOnly = true;
                try { body.getBrain().tick(level, body); }
                finally { state.sleepCoreOnly = false; }
                VillagerSleep.tick(level, body);
                ci.cancel();
            }
        }
        @com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation(method = "gossip", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/ai/gossip/GossipContainer;transferFrom(Lnet/minecraft/world/entity/ai/gossip/GossipContainer;Lnet/minecraft/util/RandomSource;I)I"))
        private int naturality$conversation(net.minecraft.world.entity.ai.gossip.GossipContainer listenerGossip,
                net.minecraft.world.entity.ai.gossip.GossipContainer speakerGossip, net.minecraft.util.RandomSource random,
                int count, com.llamalad7.mixinextras.injector.wrapoperation.Operation<Integer> original,
                ServerLevel level, Villager speaker, long timestamp) {
            var before = GossipReactions.snapshot(listenerGossip);
            int result = original.call(listenerGossip, speakerGossip, random, count);
            GossipReactions.transferred(level, (Villager)(Object)this, speaker, before);
            return result;
        }
        @Inject(method = "tick", at = @At("TAIL"))
        private void naturality$gossipSounds(CallbackInfo ci) {
            var body = (Villager)(Object)this;
            if (body.level() instanceof ServerLevel level) GossipReactions.tick(level, body);
        }
        @ModifyConstant(method = "gossip", constant = @Constant(longValue = 1200L), require = 2)
        private long naturality$moreGossip(long original) {
            return naturality.config.startup.FeatureConfig.Feature.REPUTATION.enabled() ? 800L : original;
        }
        @Shadow private void updateSpecialPrices(Player player) { throw new AssertionError(); }
        @Unique private int naturality$priceSignature = -1;
        @Unique private static int naturality$signature(Villager body, Player player) {
            var hero = player.getEffect(MobEffects.HERO_OF_THE_VILLAGE);
            return java.util.Objects.hash(player.getUUID(), Reputation.score(player),
                body.getGossips().getReputation(player.getUUID(), _ -> true), hero == null ? -1 : hero.getAmplifier());
        }
        @Inject(method = "customServerAiStep", at = @At("TAIL"))
        private void naturality$refreshPrices(ServerLevel level, CallbackInfo ci) {
            GossipReactions.focus(level, (Villager)(Object)this);
            var trader = ((Villager)(Object)this).getTradingPlayer();
            if (trader != null && naturality$signature((Villager)(Object)this, trader) != naturality$priceSignature) updateSpecialPrices(trader);
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
            int gossip = body.getGossips().getReputation(player.getUUID(), _ -> true);
            naturality$priceSignature = naturality$signature(body, player);
            var offers = body.getOffers();
            for (var offer : offers) {
                offer.resetSpecialPriceDiff();
                offer.addToSpecialPriceDiff(Reputation.priceDiff(offer.getBaseCostA().getCount(), Reputation.score(player)));
                offer.addToSpecialPriceDiff(-net.minecraft.util.Mth.floor(gossip * offer.getPriceMultiplier()));
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
            var player = body.getTradingPlayer();
            if (player != null) updateSpecialPrices(player);
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
            // Prices read native gossip directly; defender checks retain the custom zero-score threshold.
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
            if (event >= GossipReactions.LISTEN_POSITIVE && event <= GossipReactions.CONVERSE) {
                var state = Reputation.state((Villager)(Object)this);
                state.gossipStart = ((Villager)(Object)this).tickCount;
                state.gossipReaction = event;
                ci.cancel();
            }
            if (event == Reputation.NOD_EVENT) {
                var body = (Villager)(Object)this;
                Reputation.state(body).nodStart = body.tickCount; ci.cancel();
            }
        }
    }
}
