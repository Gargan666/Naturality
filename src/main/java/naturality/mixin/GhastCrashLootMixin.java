package naturality.mixin;

import naturality.ghast.GhastCrash;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerLevel.class)
public abstract class GhastCrashLootMixin {
    @Inject(method="addFreshEntity",at=@At("HEAD"),cancellable=true)
    private void naturality$holdLoot(Entity entity,CallbackInfoReturnable<Boolean> ci) {
        if (GhastCrash.captureSpawn(entity)) ci.setReturnValue(true);
    }
}
