package naturality.client.mixin;

import naturality.client.menu.MenuPreview;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerLevel.class)
public abstract class MenuPreviewMobMixin {
    @Inject(method = "addFreshEntity", at = @At("HEAD"), cancellable = true)
    private void naturality$noMobs(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (entity instanceof net.minecraft.world.entity.Mob && ((ServerLevel)(Object)this).getServer() instanceof net.minecraft.client.server.IntegratedServer server && MenuPreview.owns(server))
            cir.setReturnValue(false);
    }
}
