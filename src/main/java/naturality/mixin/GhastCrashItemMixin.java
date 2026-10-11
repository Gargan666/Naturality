package naturality.mixin;

import naturality.ghast.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Also holds equipment/leads shed outside the main death-loot call. */
@Mixin(Entity.class)
public abstract class GhastCrashItemMixin {
    @Inject(method="spawnAtLocation(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/entity/item/ItemEntity;",at=@At("HEAD"),cancellable=true)
    private void naturality$holdAttachedDrops(ServerLevel level,ItemStack stack,Vec3 offset,CallbackInfoReturnable<ItemEntity> ci) {
        if (!((Object)this instanceof LivingEntity entity) || !GhastMotion.supported(entity) || !entity.isDeadOrDying()) return;
        GhastCrash.Flight flight=((GhastCrashBody)entity).naturality$flight();
        if (flight==null || stack.isEmpty()) return;
        flight.drops.add(stack.copy());
        ci.setReturnValue(new ItemEntity(level,entity.getX(),entity.getY(),entity.getZ(),stack.copy()));
    }
}
