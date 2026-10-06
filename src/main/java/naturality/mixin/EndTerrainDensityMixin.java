package naturality.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import naturality.worldgen.EndTerrainDensity;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.NoiseRouter;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(NoiseBasedChunkGenerator.class)
public abstract class EndTerrainDensityMixin {
    @WrapOperation(method = {"doFill", "iterateNoiseColumn"}, at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/level/levelgen/NoiseRouter;finalDensity()Lnet/minecraft/world/level/levelgen/densityfunction/DensityFunction;"))
    private DensityFunction naturality$outerTerrain(NoiseRouter router, Operation<DensityFunction> original) {
        DensityFunction density = original.call(router);
        return ((NoiseBasedChunkGenerator)(Object)this).stable(NoiseGeneratorSettings.END)
            ? new EndTerrainDensity(density) : density;
    }
}
