package naturality.worldgen.end.materials.mixins;

import java.util.Set;
import naturality.worldgen.end.materials.EndStoneLayers;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.material.rule.MaterialRule;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.EndIslandFeature;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Feature integration hooks; each nested type retains its independent Mixin target and gate. */
public final class MaterialsMixins {
    private MaterialsMixins() {}

    @Mixin(NoiseBasedChunkGenerator.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class EndStoneTerrainMixin {
        @Inject(method = "buildSurface", at = @At("RETURN"))
        private void naturality$endLayers(ChunkAccess chunk, NoiseChunk noise, RandomState random,
                BiomeManager biomes, Set<Holder<Biome>> possibleBiomes, MaterialRule rule, CallbackInfo ci) {
            if (((NoiseBasedChunkGenerator)(Object)this).stable(NoiseGeneratorSettings.END)) {
                var sampler=random.getSampler(new naturality.worldgen.end.terrain.EndTerrainDensity(
                    ((NoiseBasedChunkGenerator)(Object)this).generatorSettings().value().noiseRouter().finalDensity()));
                EndStoneLayers.apply(chunk, random.getOrCreateRandomFactory(naturality.Naturality.id("lirestone_patches"))
                    .at(0, 0, 0).nextLong(),(naturality.worldgen.end.terrain.EndTerrainDensity.Sampler)sampler,
                    ((NoiseBasedChunkGenerator)(Object)this).getBiomeSource().createCachingResolver(random),biomes);
            }
        }
    }

    @Mixin(EndIslandFeature.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class SmallEndIslandLayersMixin {
        @com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod(method = "place")
        private boolean naturality$outerRocks(WorldGenLevel level, ChunkGenerator generator, RandomSource random,
                BlockPos origin, com.llamalad7.mixinextras.injector.wrapoperation.Operation<Boolean> original) {
            if (generator instanceof net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator noise
                    && noise.stable(net.minecraft.world.level.levelgen.NoiseGeneratorSettings.END)) {
                // Outer outcrops now belong to the density field: tall, tapered rocks that also
                // participate in height queries and surface layering. Do not add vanilla discs.
                if (Math.hypot(origin.getX(), origin.getZ()) > 1024) return false;
            }
            return original.call(level, generator, random, origin);
        }
        @Inject(method = "place", at = @At("RETURN"))
        private void naturality$smallIsland(WorldGenLevel level, ChunkGenerator generator, RandomSource random,
                BlockPos origin, CallbackInfoReturnable<Boolean> cir) {
            if (cir.getReturnValueZ()) EndStoneLayers.smallIsland(level, origin);
        }
    }
}
