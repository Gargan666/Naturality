package naturality.mixin;

import java.util.Comparator;
import java.util.function.Predicate;
import naturality.villager.ButcherWork;
import naturality.villager.VillagerWorkVisuals;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Adds butcher food to vanilla temptation, retaining each animal goal priority and speed. */
@Mixin(TemptGoal.class)
public abstract class ButcherTemptGoalMixin {
    @Shadow protected Mob mob;
    @Shadow protected double speedModifier;
    @Shadow private Predicate<ItemStack> items;
    @Shadow private int calmDown;
    @Shadow private boolean isRunning;
    @Shadow private double stopDistance;
    @Unique private Villager naturality$butcher;

    @Inject(method = "canUse", at = @At("RETURN"), cancellable = true)
    private void naturality$findButcher(CallbackInfoReturnable<Boolean> cir) {
        naturality$butcher = null;
        if (cir.getReturnValueZ() || calmDown > 0 || !(mob instanceof Animal animal)
                || !ButcherWork.isLivestock(animal)) return;
        double range = mob.getAttributeValue(Attributes.TEMPT_RANGE);
        naturality$butcher = mob.level().getEntitiesOfClass(Villager.class, mob.getBoundingBox().inflate(range),
            villager -> villager.isAlive() && !villager.isBaby() && !villager.isTrading()
                && villager.getVillagerData().profession().is(VillagerProfession.BUTCHER)
                && !((VillagerWorkVisuals)villager).naturality$isDisplayingTrade()
                && mob.distanceToSqr(villager) <= range * range && !mob.hasPassenger(villager)
                && items.test(villager.getMainHandItem()))
            .stream().min(Comparator.comparingDouble(mob::distanceToSqr)).orElse(null);
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
        if (naturality$butcher == null) return;
        mob.getLookControl().setLookAt(naturality$butcher, mob.getMaxHeadYRot() + 20, mob.getMaxHeadXRot());
        if (mob.distanceToSqr(naturality$butcher) < stopDistance * stopDistance) mob.getNavigation().stop();
        else mob.getNavigation().moveTo(naturality$butcher, speedModifier);
        ci.cancel();
    }
}
