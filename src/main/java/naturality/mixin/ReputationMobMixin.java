package naturality.mixin;

import naturality.villager.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.monster.zombie.ZombieVillager;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(Mob.class)
public abstract class ReputationMobMixin implements ReputationHolder {
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
    @Redirect(method = "convertTo(Lnet/minecraft/world/entity/EntityType;Lnet/minecraft/world/entity/ConversionParams;Lnet/minecraft/world/entity/EntitySpawnReason;Lnet/minecraft/world/entity/ConversionParams$AfterConversion;)Lnet/minecraft/world/entity/Mob;",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/ConversionParams$AfterConversion;finalizeConversion(Lnet/minecraft/world/entity/Mob;)V"))
    private void naturality$lineage(ConversionParams.AfterConversion<Mob> callback, Mob converted) {
        if (((Object)this instanceof Villager || (Object)this instanceof ZombieVillager)
                && (converted instanceof Villager || converted instanceof ZombieVillager))
            Reputation.state(converted).copyFrom(naturality$rep);
        callback.finalizeConversion(converted);
    }
    @Inject(method = "canAttack(Lnet/minecraft/world/entity/LivingEntity;)Z", at = @At("HEAD"), cancellable = true)
    private void naturality$allies(LivingEntity target, CallbackInfoReturnable<Boolean> cir) {
        if (target instanceof Player player && Reputation.illagerAlly((Mob)(Object)this, player)) cir.setReturnValue(false);
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
