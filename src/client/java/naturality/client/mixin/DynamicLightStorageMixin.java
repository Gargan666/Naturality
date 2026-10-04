package naturality.client.mixin;

import naturality.client.lighting.DynamicLighting;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.lighting.LightEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LightEngine.class)
public abstract class DynamicLightStorageMixin {
    @Inject(method = "getLightValue", at = @At("RETURN"), cancellable = true)
    private void naturality$virtualField(BlockPos position, CallbackInfoReturnable<Integer> cir) {
        // Empty high sections have no vanilla storage. The identity check also
        // excludes skylight and every server/world engine from this overlay.
        cir.setReturnValue(Math.max(cir.getReturnValueI(), DynamicLighting.blockLight(this, position.asLong())));
    }
}
