package naturality.entities.arrows.mixins;

import naturality.entities.arrows.ArrowImpactPayload;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.phys.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractArrow.class)
@org.jspecify.annotations.NullMarked
public abstract class ArrowImpactMixin {
    @Inject(method = "onHitEntity", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;setArrowCount(I)V", shift = At.Shift.AFTER))
    private void naturality$impact(EntityHitResult hit, CallbackInfo ci) {
        var arrow = (AbstractArrow)(Object)this;
        var mob = (LivingEntity)hit.getEntity();
        var packet = new ArrowImpactPayload(mob.level().dimension().identifier(), mob.getId(), mob.getArrowCount() - 1,
            hit.getLocation().subtract(mob.position()), arrow.getDeltaMovement().normalize(), mob.yBodyRot);
        for (var viewer : PlayerLookup.tracking(mob))
            if (ServerPlayNetworking.canSend(viewer, ArrowImpactPayload.TYPE)) ServerPlayNetworking.send(viewer, packet);
    }
}
