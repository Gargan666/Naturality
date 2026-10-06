package naturality.mixin;

import naturality.villager.Reputation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(LivingEntity.class)
public abstract class ReputationDamageMixin {
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
