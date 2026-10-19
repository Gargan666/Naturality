package naturality.worldgen.nether.terrain.mixins;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import naturality.worldgen.nether.terrain.NetherTerrain;
import net.minecraft.core.Holder;
import net.minecraft.world.level.chunk.CarvingMask;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.NoiseRouter;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunction;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Standard Nether only; geometry runs before biome surfaces, carving and decoration. */
public final class NetherTerrainMixins {
    private NetherTerrainMixins() {}

    @Mixin(NoiseBasedChunkGenerator.class)
    @NullMarked
    public static abstract class NetherTerrainMixin {
        @Shadow public abstract Holder<NoiseGeneratorSettings> generatorSettings();

        @WrapOperation(method = {"doFill", "iterateNoiseColumn"}, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/levelgen/NoiseRouter;finalDensity()Lnet/minecraft/world/level/levelgen/densityfunction/DensityFunction;"))
        private DensityFunction naturality$terrain(NoiseRouter router, Operation<DensityFunction> original) {
            DensityFunction density = original.call(router);
            return ((NoiseBasedChunkGenerator)(Object)this).stable(NoiseGeneratorSettings.NETHER)
                ? new NetherTerrain(density) : density;
        }

        @WrapOperation(method = {"doFill", "iterateNoiseColumn", "applyCarvingMask"}, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/levelgen/NoiseChunk;aquifer()Lnet/minecraft/world/level/levelgen/Aquifer;"))
        private Aquifer naturality$lakes(NoiseChunk noise, Operation<Aquifer> original) {
            Aquifer aquifer = original.call(noise);
            return ((NoiseBasedChunkGenerator)(Object)this).stable(NoiseGeneratorSettings.NETHER)
                ? NetherTerrain.aquifer(aquifer, noise, generatorSettings().value().noiseRouter().finalDensity()) : aquifer;
        }

        @ModifyVariable(method = "applyCarvingMask", at = @At("HEAD"), argsOnly = true)
        private CarvingMask.@Nullable Filter naturality$preserveTerrain(CarvingMask.@Nullable Filter filter,
                @Local(argsOnly = true) ChunkAccess chunk, @Local(argsOnly = true) NoiseChunk noise) {
            return ((NoiseBasedChunkGenerator)(Object)this).stable(NoiseGeneratorSettings.NETHER)
                ? NetherTerrain.protect(chunk, noise, generatorSettings().value().noiseRouter().finalDensity(), filter) : filter;
        }
    }
}
