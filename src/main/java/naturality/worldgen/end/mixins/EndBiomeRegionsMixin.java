package naturality.worldgen.end.mixins;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import naturality.worldgen.end.endwilds.EndWilds;
import naturality.worldgen.end.lirestonecrags.LirestoneCrags;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.TheEndBiomeSource;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(TheEndBiomeSource.class)
@org.jspecify.annotations.NullMarked
public abstract class EndBiomeRegionsMixin {
    @SuppressWarnings("null") @Shadow @Final private Holder<Biome> highlands;
    @SuppressWarnings("null") @Shadow @Final private Holder<Biome> midlands;
    @SuppressWarnings("null") @Shadow @Final private Holder<Biome> islands;
    @SuppressWarnings("null") @Shadow @Final private Holder<Biome> barrens;
    @Unique private volatile @org.jspecify.annotations.Nullable Holder<Biome> naturality$wilds;
    @Unique private volatile @org.jspecify.annotations.Nullable Holder<Biome> naturality$crags;

    @WrapMethod(method="getNoiseBiome")
    private Holder<Biome> naturality$regions(int x,int y,int z,Climate.Sampler sampler,Operation<Holder<Biome>> original) {
        var selected=original.call(x,y,z,sampler);
        if(selected.is(Biomes.THE_END))return selected;
        if(naturality.config.startup.FeatureConfig.Feature.END_WILDS.enabled() && naturality$wilds==null
                || naturality.config.startup.FeatureConfig.Feature.LIRESTONE_CRAGS.enabled() && naturality$crags==null) {
            for(var biome:((TheEndBiomeSource)(Object)this).possibleBiomes()) {
                if(biome.is(EndWilds.BIOME))naturality$wilds=biome;
                if(biome.is(LirestoneCrags.BIOME))naturality$crags=biome;
            }
        }
        var noise=((net.fabricmc.fabric.impl.biome.MultiNoiseSamplerHooks)(Object)sampler).fabric_getEndBiomesSampler();
        // Quart coordinates: regional scale 1,024 blocks, with gentle 320-block edge detail.
        double region=noise.get(x/256.0,0,z/256.0)
            +.15*noise.get(x/80.0,19,z/80.0);
        if(region>.22 && naturality.config.startup.FeatureConfig.Feature.END_WILDS.enabled() && naturality$wilds!=null)return naturality$wilds;
        if(region<-.22 && naturality.config.startup.FeatureConfig.Feature.LIRESTONE_CRAGS.enabled() && naturality$crags!=null)return naturality$crags;
        if(!selected.is(EndWilds.BIOME) && !selected.is(LirestoneCrags.BIOME))return selected;
        // Restore the native ordinary biome in the separating band.
        int blockX=((x>>2)*2+1)*8,blockZ=((z>>2)*2+1)*8;
        double height=sampler.erosion().sampleValue(blockX,y*4,blockZ);
        return height>.25?highlands:height>=-.0625?midlands:height<-.21875?islands:barrens;
    }
}
