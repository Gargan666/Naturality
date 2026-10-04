package naturality.mixin;

import naturality.villager.PlayerFishingReturn;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(FishingHook.class)
public abstract class FishingHookReturnMixin {
    @Redirect(method = "retrieve", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/level/Level;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z"))
    private boolean naturality$returnCatchOnHook(Level level, Entity entity) {
        FishingHook hook = (FishingHook)(Object)this;
        if (entity instanceof ItemEntity item && level instanceof ServerLevel server && hook.getPlayerOwner() != null) {
            return level.addFreshEntity(new PlayerFishingReturn(server, hook.getPlayerOwner(), hook.position(), item.getItem()));
        }
        return level.addFreshEntity(entity);
    }
}
