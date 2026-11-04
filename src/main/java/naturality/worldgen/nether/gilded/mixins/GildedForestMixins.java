package naturality.worldgen.nether.gilded.mixins;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import naturality.worldgen.nether.gilded.GildedForest;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import naturality.blocks.gilded.GildedBlocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.HugeFungusFeature;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.NoiseRouter;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;

public final class GildedForestMixins {
    private GildedForestMixins() {}

    @Mixin(RandomState.class)
    public abstract static class Terrain {
        @Shadow @Final private NoiseRouter router;

        @ModifyReturnValue(method = "createClimateSampler", at = @At("RETURN"))
        private Climate.Sampler naturality$terrain(Climate.Sampler climate, SamplerContext context) {
            var state = (RandomState)(Object)this;
            var input = router.finalDensity();
            GildedForest.terrain(climate, () -> state.getSampler(
                naturality.config.startup.FeatureConfig.Feature.NETHER_LAVA_LAKES.enabled()
                    ? new naturality.worldgen.nether.lavalakes.NetherLavaLakes(input) : input)
                .bind(context));
            return climate;
        }
    }

    @Mixin(HugeFungusFeature.class)
    public abstract static class FungusVines {
        @WrapOperation(method = "placeHat", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/state/BlockState;is(Ljava/lang/Object;)Z"))
        private boolean naturality$gildedVines(BlockState state, Object block, Operation<Boolean> original) {
            return original.call(state, block) || state.is(GildedBlocks.WART_BLOCK);
        }
    }

    @Mixin(MultiNoiseBiomeSource.class)
    public abstract static class Regions {
        @WrapMethod(method = "createResolver")
        private BiomeResolver naturality$regions(Climate.Sampler sampler, Operation<BiomeResolver> original) {
            return GildedForest.wrap((MultiNoiseBiomeSource)(Object)this, sampler, original.call(sampler));
        }

        @WrapMethod(method = "createResolverForChunk")
        private BiomeResolver naturality$chunkRegions(Climate.Sampler sampler, int x, int y, int z,
                int sizeX, int sizeY, int sizeZ, Operation<BiomeResolver> original) {
            return GildedForest.wrap((MultiNoiseBiomeSource)(Object)this, sampler,
                original.call(sampler, x, y, z, sizeX, sizeY, sizeZ));
        }
    }
}
