package naturality.client.mixin;

import naturality.client.menu.MenuPreview;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(WorldGenRegion.class)
public abstract class MenuPreviewGenerationMobMixin {
    @Inject(method = "addFreshEntity", at = @At("HEAD"), cancellable = true)
    private void naturality$noPopulation(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (entity instanceof net.minecraft.world.entity.Mob && ((WorldGenRegion)(Object)this).getServer() instanceof net.minecraft.client.server.IntegratedServer server && MenuPreview.owns(server))
            cir.setReturnValue(false);
    }
}
