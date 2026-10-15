package naturality.systems.villagerwork.professions.mixins;

import naturality.systems.villagerwork.professions.ButcherWork;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.animal.Animal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.Comparator;
import java.util.function.Predicate;
import naturality.systems.villagerwork.framework.VillagerWork.VillagerWorkVisuals;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import naturality.systems.villagerwork.professions.ProfessionWork;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.behavior.WorkAtComposter;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ComposterBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import org.spongepowered.asm.mixin.injection.Redirect;
import net.minecraft.world.level.block.FarmlandBlock;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.npc.villager.VillagerData;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** Feature integration hooks; each nested type retains its independent Mixin target and gate. */
public final class VillagerworkProfessionsMixins {
    private VillagerworkProfessionsMixins() {}

    @Mixin(Animal.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class ButcherBreedingMixin {
        @Inject(method = "finalizeSpawnChildFromBreeding", at = @At("TAIL"))
        private void naturality$born(ServerLevel level, Animal partner, AgeableMob child, CallbackInfo ci) {
            if (child != null) ButcherWork.born((Animal)(Object)this, partner, level.getGameTime());
        }
    }

    /** Adds butcher food to vanilla temptation, retaining each animal goal priority and speed. */
    @Mixin(TemptGoal.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class ButcherTemptGoalMixin {
        @SuppressWarnings("null") @Shadow protected Mob mob;
        @Shadow protected double speedModifier;
        @SuppressWarnings("null") @Shadow private Predicate<ItemStack> items;
        @Shadow private int calmDown;
        @Shadow private boolean isRunning;
        @Shadow private double stopDistance;
        @Unique private @org.jspecify.annotations.Nullable Villager naturality$butcher;

        @Inject(method = "canUse", at = @At("RETURN"), cancellable = true)
        private void naturality$findButcher(CallbackInfoReturnable<Boolean> cir) {
            naturality$butcher = null;
            if (cir.getReturnValueZ() || calmDown > 0 || !(mob instanceof Animal animal)
                    || !ButcherWork.isLivestock(animal)) return;
            double range = mob.getAttributeValue(Attributes.TEMPT_RANGE);
            var candidate = mob.level().getEntitiesOfClass(Villager.class, mob.getBoundingBox().inflate(range),
                villager -> villager.isAlive() && !villager.isBaby() && !villager.isTrading()
                    && villager.getVillagerData().profession().is(VillagerProfession.BUTCHER)
                    && !((VillagerWorkVisuals)villager).naturality$isDisplayingTrade()
                    && mob.distanceToSqr(villager) <= range * range && !mob.hasPassenger(villager)
                    && items.test(villager.getMainHandItem()))
                .stream().min(Comparator.comparingDouble(mob::distanceToSqr));
            naturality$butcher = candidate.isPresent() ? candidate.orElseThrow() : null;
            if (naturality$butcher != null) cir.setReturnValue(true);
        }

        @Inject(method = "canContinueToUse", at = @At("HEAD"), cancellable = true)
        private void naturality$continueFollowing(CallbackInfoReturnable<Boolean> cir) {
            if (naturality$butcher != null) cir.setReturnValue(((TemptGoal)(Object)this).canUse());
        }

        @Inject(method = "start", at = @At("HEAD"), cancellable = true)
        private void naturality$startFollowing(CallbackInfo ci) {
            if (naturality$butcher != null) { isRunning = true; ci.cancel(); }
        }

        @Inject(method = "stop", at = @At("HEAD"))
        private void naturality$stopFollowing(CallbackInfo ci) { naturality$butcher = null; }

        @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
        private void naturality$followButcher(CallbackInfo ci) {
            var butcher = naturality$butcher;
            if (butcher == null) return;
            mob.getLookControl().setLookAt(butcher, mob.getMaxHeadYRot() + 20, mob.getMaxHeadXRot());
            if (mob.distanceToSqr(butcher) < stopDistance * stopDistance) mob.getNavigation().stop();
            else mob.getNavigation().moveTo(butcher, speedModifier);
            ci.cancel();
        }
    }

    @Mixin(WorkAtComposter.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class VillagerComposterMixin {
        @Redirect(method = "compostItems", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/ComposterBlock;extractProduce(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"))
        private BlockState naturality$collect(Entity entity, BlockState state, Level level, BlockPos pos) {
            var empty = state.setValue(ComposterBlock.LEVEL, 0);
            level.setBlockAndUpdate(pos, empty);
            level.gameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Context.of(entity, empty));
            level.playSound(null, pos, SoundEvents.COMPOSTER_EMPTY, SoundSource.BLOCKS, 1, 1);
            ProfessionWork.store((ServerLevel)level, (Villager)entity, new ItemStack(Items.BONE_MEAL));
            return empty;
        }
    }

    /** Keep fall damage, but never let a villager's landing destroy a planted field. */
    @Mixin(FarmlandBlock.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class VillagerFarmlandLandingMixin {
        @Inject(method = "fallOn", at = @At("HEAD"), cancellable = true)
        private void naturality$protectFromVillager(Level level, BlockState state, BlockPos pos, Entity entity,
                double fallDistance, CallbackInfo ci) {
            if (entity instanceof Villager) {
                var farmland = (FarmlandBlock)(Object)this;
                entity.causeFallDamage(fallDistance * (1.0F - farmland.getFallDistanceReduction()),
                    1.0F, entity.damageSources().fall());
                ci.cancel();
            }
        }
    }

    @Mixin(Villager.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class VillagerSuppliesMixin {
        @Unique private boolean naturality$pendingFarmSupplies;
        @Unique private boolean naturality$butcherSupplied;
        @Unique private boolean naturality$loading;

        @Unique private void naturality$supplyButcher() {
            if (!naturality.config.startup.FeatureConfig.Feature.BUTCHER_WORK.enabled()) return;
            var body = (Villager)(Object)this;
            if (!naturality$loading && !naturality$butcherSupplied
                    && body.getVillagerData().profession().is(VillagerProfession.BUTCHER)) {
                naturality$butcherSupplied = true;
                for (var item : new net.minecraft.world.item.Item[]{Items.WHEAT, Items.CARROT, Items.WHEAT_SEEDS})
                    body.getInventory().addItem(new ItemStack(item, 4));
            }
        }

        @Unique private void naturality$supplyFarmer() {
            if (!naturality.config.startup.FeatureConfig.Feature.FARMER_WORK.enabled()) return;
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
}
