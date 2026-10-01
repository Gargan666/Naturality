package naturality.client.mixin;

import naturality.client.lighting.DynamicLighting;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.BlockLightEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockLightEngine.class)
public abstract class DynamicBlockLightMixin {
    @Inject(method = "getEmission", at = @At("RETURN"), cancellable = true)
    private void naturality$dynamicEmission(long position, BlockState block, CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(Math.max(cir.getReturnValueI(), DynamicLighting.emission(this, position)));
    }
}
