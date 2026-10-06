package naturality.mixin;

import naturality.worldgen.EndIslandMaskCache;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunction;
import net.minecraft.world.level.levelgen.densityfunction.DensitySampler;
import net.minecraft.world.level.levelgen.densityfunction.generator.EndIslandFunction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EndIslandFunction.class)
public abstract class EndIslandMaskCacheMixin {
    @Inject(method = "compileSampler", at = @At("RETURN"), cancellable = true)
    private void naturality$cacheIslandMask(DensityFunction.CompileContext context, CallbackInfoReturnable<DensitySampler> cir) {
        cir.setReturnValue(new EndIslandMaskCache(cir.getReturnValue()));
    }
}
