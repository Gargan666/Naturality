package naturality.mixin;

import java.util.Set;
import naturality.worldgen.EndStoneLayers;
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

@Mixin(NoiseBasedChunkGenerator.class)
public abstract class EndStoneTerrainMixin {
    @Inject(method = "buildSurface", at = @At("RETURN"))
    private void naturality$endLayers(ChunkAccess chunk, NoiseChunk noise, RandomState random,
            BiomeManager biomes, Set<Holder<Biome>> possibleBiomes, MaterialRule rule, CallbackInfo ci) {
        if (((NoiseBasedChunkGenerator)(Object)this).stable(NoiseGeneratorSettings.END)) {
            var sampler=random.getSampler(new naturality.worldgen.EndTerrainDensity(
                ((NoiseBasedChunkGenerator)(Object)this).generatorSettings().value().noiseRouter().finalDensity()));
            EndStoneLayers.apply(chunk, random.getOrCreateRandomFactory(naturality.Naturality.id("lirestone_patches"))
                .at(0, 0, 0).nextLong(),(naturality.worldgen.EndTerrainDensity.Sampler)sampler);
        }
    }
}
