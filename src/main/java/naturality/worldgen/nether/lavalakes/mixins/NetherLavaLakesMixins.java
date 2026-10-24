package naturality.worldgen.nether.lavalakes.mixins;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import naturality.worldgen.nether.lavalakes.NetherLavaLakes;
import net.minecraft.core.Holder;
import java.util.Set;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.CarvingMask;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.NoiseRouter;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.VerticalAnchor;
import net.minecraft.world.level.levelgen.WorldGenerationContext;
import net.minecraft.world.level.levelgen.material.MaterialRuleContext;
import net.minecraft.world.level.levelgen.material.rule.MaterialRule;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunction;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Standard Nether only; lake beds and fluids run before surfaces and decoration. */
public final class NetherLavaLakesMixins {
    private NetherLavaLakesMixins() {}

    @Mixin(NoiseBasedChunkGenerator.class)
    @NullMarked
    public static abstract class NetherLavaLakesMixin {
        @Shadow public abstract Holder<NoiseGeneratorSettings> generatorSettings();

        @WrapMethod(method = "buildSurface")
        private void naturality$localSurface(ChunkAccess chunk, NoiseChunk noise, RandomState random,
                BiomeManager biomes, Set<Holder<Biome>> possibleBiomes, MaterialRule rule, Operation<Void> original) {
            if (((NoiseBasedChunkGenerator)(Object)this).stable(NoiseGeneratorSettings.NETHER)) {
                NetherLavaLakes.withSurfaceLevel(noise, generatorSettings().value().noiseRouter().finalDensity(),
                    () -> original.call(chunk, noise, random, biomes, possibleBiomes, rule));
            } else original.call(chunk, noise, random, biomes, possibleBiomes, rule);
        }

        @WrapMethod(method = "applyCarvingMask")
        private void naturality$localCarvedSurface(ChunkAccess chunk, CarvingMask mask, RandomState random,
                MaterialRule rule, WorldGenerationContext context, NoiseChunk noise,
                Function<BlockPos, Holder<Biome>> biomes, CarvingMask.@Nullable Filter filter, Operation<Void> original) {
            if (((NoiseBasedChunkGenerator)(Object)this).stable(NoiseGeneratorSettings.NETHER)) {
                NetherLavaLakes.withSurfaceLevel(noise, generatorSettings().value().noiseRouter().finalDensity(),
                    () -> original.call(chunk, mask, random, rule, context, noise, biomes, filter));
            } else original.call(chunk, mask, random, rule, context, noise, biomes, filter);
        }

        @WrapOperation(method = {"doFill", "iterateNoiseColumn"}, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/levelgen/NoiseRouter;finalDensity()Lnet/minecraft/world/level/levelgen/densityfunction/DensityFunction;"))
        private DensityFunction naturality$lakeBeds(NoiseRouter router, Operation<DensityFunction> original) {
            DensityFunction density = original.call(router);
            return ((NoiseBasedChunkGenerator)(Object)this).stable(NoiseGeneratorSettings.NETHER)
                ? new NetherLavaLakes(density) : density;
        }

        @WrapOperation(method = {"doFill", "iterateNoiseColumn", "applyCarvingMask"}, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/levelgen/NoiseChunk;aquifer()Lnet/minecraft/world/level/levelgen/Aquifer;"))
        private Aquifer naturality$lakes(NoiseChunk noise, Operation<Aquifer> original) {
            Aquifer aquifer = original.call(noise);
            return ((NoiseBasedChunkGenerator)(Object)this).stable(NoiseGeneratorSettings.NETHER)
                ? NetherLavaLakes.aquifer(aquifer, noise, generatorSettings().value().noiseRouter().finalDensity()) : aquifer;
        }

        @ModifyVariable(method = "applyCarvingMask", at = @At("HEAD"), argsOnly = true)
        private CarvingMask.@Nullable Filter naturality$preserveLakeBeds(CarvingMask.@Nullable Filter filter,
                @Local(argsOnly = true) ChunkAccess chunk, @Local(argsOnly = true) NoiseChunk noise) {
            return ((NoiseBasedChunkGenerator)(Object)this).stable(NoiseGeneratorSettings.NETHER)
                ? NetherLavaLakes.protect(chunk, noise, generatorSettings().value().noiseRouter().finalDensity(), filter) : filter;
        }
    }

    @Mixin(MaterialRuleContext.class)
    public static abstract class NetherLavaSurfaceMixin {
        @Shadow public abstract int blockX();
        @Shadow public abstract int blockZ();

        @ModifyReturnValue(method = "resolveAnchorY", at = @At("RETURN"))
        private int naturality$localLakeAnchor(int original, VerticalAnchor anchor) {
            return NetherLavaLakes.surfaceAnchor(original, anchor, blockX(), blockZ());
        }
    }

    @Mixin(ChunkGenerator.class)
    public static abstract class NetherLavaUpdatesMixin {
        @Inject(method = "applyBiomeDecoration", at = @At("RETURN"))
        private void naturality$updateExposedLava(WorldGenLevel level, ChunkAccess chunk,
                StructureManager structures, CallbackInfo ci) {
            if ((Object)this instanceof NoiseBasedChunkGenerator generator && generator.stable(NoiseGeneratorSettings.NETHER))
                NetherLavaLakes.updateExposedLava(chunk);
        }
    }
}
