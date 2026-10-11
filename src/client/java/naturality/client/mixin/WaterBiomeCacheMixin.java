package naturality.client.mixin;

import naturality.client.fluid.WaterVisuals;
import net.minecraft.client.multiplayer.ClientChunkCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientChunkCache.class)
public abstract class WaterBiomeCacheMixin {
    @Inject(method = "replaceBiomes", at = @At("TAIL"))
    private void naturality$refreshWaterBiomes(CallbackInfo ci) { WaterVisuals.invalidateBiomes(); }
}
