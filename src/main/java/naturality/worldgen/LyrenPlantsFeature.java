package naturality.worldgen;

import com.mojang.serialization.MapCodec;
import naturality.NaturalityBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;

/** A mixed grass patch; the placed feature supplies vanilla plains patch density and spread. */
public record LyrenPlantsFeature() implements Feature {
    public static final MapCodec<LyrenPlantsFeature> CODEC=MapCodec.unit(LyrenPlantsFeature::new);
    @Override public MapCodec<LyrenPlantsFeature> codec() {return CODEC;}

    @Override public boolean place(WorldGenLevel level,ChunkGenerator generator,RandomSource random,BlockPos pos) {
        if (!naturality.config.Feature.LYREN_PLANTS.enabled()) return false;
        if(level instanceof net.minecraft.server.level.WorldGenRegion region && !region.isWithinWriteZone(pos))return false;
        if(!level.ensureCanWrite(pos) || !level.getBlockState(pos).isAir()
            || !level.getBlockState(pos.below()).is(NaturalityBlocks.LYREN_HEATH)
            || !level.getBiome(pos).is(EndWilds.BIOME))return false;
        var state=(random.nextBoolean()?NaturalityBlocks.LYREN_SPROUT:NaturalityBlocks.LYREN_GROWTH).defaultBlockState();
        if(!state.canSurvive(level,pos))return false;
        return level.setBlock(pos,state,2);
    }
}
