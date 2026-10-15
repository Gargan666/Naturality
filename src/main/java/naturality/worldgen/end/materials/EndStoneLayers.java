package naturality.worldgen.end.materials;
import naturality.worldgen.end.endwilds.EndWilds;
import naturality.worldgen.end.lirestonecrags.LirestoneCrags;
import naturality.worldgen.end.lirestonecrags.LirestoneCrags.LirestonePatches;
import naturality.worldgen.end.endwilds.EndWilds.LyrenHeathPatches;
import naturality.worldgen.end.terrain.EndTerrainDensity;

import naturality.bootstrap.ContentRegistration.NaturalityBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkAccess;

public final class EndStoneLayers {
    private EndStoneLayers() {}

    public static void apply(ChunkAccess chunk, long seed) {
        apply(chunk,seed,null);
    }

    public static void apply(ChunkAccess chunk, long seed, EndTerrainDensity.Sampler terrain) {
        apply(chunk,seed,terrain,null);
    }

    public static void apply(ChunkAccess chunk, long seed, EndTerrainDensity.Sampler terrain,
            net.minecraft.world.level.biome.BiomeResolver biomes) {
        apply(chunk,seed,terrain,biomes,null);
    }

    public static void apply(ChunkAccess chunk, long seed, EndTerrainDensity.Sampler terrain,
            net.minecraft.world.level.biome.BiomeResolver biomes,
            net.minecraft.world.level.biome.BiomeManager surfaceBiomes) {
        var patches = new LirestonePatches(seed);
        var heathPatches=surfaceBiomes==null?null:new LyrenHeathPatches(seed);
        boolean[] crags=biomes==null?null:LirestoneCrags.conversionColumns(biomes,chunk.getPos().getMinBlockX(),chunk.getPos().getMinBlockZ(),seed);
        var pos = new BlockPos.MutableBlockPos();
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
            double abundance = patches.regionalAbundance(chunk.getPos().getMinBlockX() + x,
                chunk.getPos().getMinBlockZ() + z);
            boolean allLirestone=crags!=null && crags[x+z*16];
            var surface = terrain==null || allLirestone ? null : terrain.surfaceMaterials(chunk.getPos().getMinBlockX()+x,
                chunk.getPos().getMinBlockZ()+z);
            boolean entire = surface!=null && patches.converts(surface.smallIsland());
            double rim = 1;
            int depth = 0;
            for (int y = chunk.getMaxY(); y >= chunk.getMinY(); y--) {
                pos.set(chunk.getPos().getMinBlockX() + x, y, chunk.getPos().getMinBlockZ() + z);
                var state = chunk.getBlockState(pos);
                if (state.is(Blocks.END_STONE) || state.is(NaturalityBlocks.SMOOTH_ENDSTONE)) {
                    depth++;
                    if(depth==1 && surface!=null)rim=surface.rimAffinity(y);
                    boolean deposit=allLirestone || (entire && surface != null && surface.inSmallIsland(y))
                        || patches.contains(pos.getX(),y,pos.getZ(),depth,abundance,rim);
                    // The noise terrain starts as End Stone; its deeper layers become smooth in this pass.
                    boolean smooth=depth>2 || state.is(NaturalityBlocks.SMOOTH_ENDSTONE);
                    var material=deposit
                        ? (smooth ? NaturalityBlocks.SMOOTH_LIRESTONE : NaturalityBlocks.LIRESTONE)
                        : (depth>2 ? NaturalityBlocks.SMOOTH_ENDSTONE : Blocks.END_STONE);
                    // Patchy caps on exposed ledges, including shelves beneath island overhangs.
                    if(depth==1 && !deposit && surfaceBiomes!=null && surfaceBiomes.getBiome(pos).is(EndWilds.BIOME)
                        && chunk.getBlockState(pos.above()).isAir()
                        && heathPatches != null && heathPatches.contains(pos.getX(),pos.getZ()))material=NaturalityBlocks.LYREN_HEATH;
                    if (!state.is(material)) chunk.setBlockState(pos, material.defaultBlockState());
                } else if (state.is(NaturalityBlocks.LIRESTONE) || state.is(NaturalityBlocks.SMOOTH_LIRESTONE)
                    || state.is(NaturalityBlocks.LYREN_HEATH)) depth++;
                else depth = 0;
            }
        }
    }

    public static void smallIsland(WorldGenLevel level, BlockPos origin) {
        var pos = new BlockPos.MutableBlockPos();
        // Vanilla's feature starts with radius 4-6 and shrinks on every layer.
        for (int x = -8; x <= 8; x++) for (int z = -8; z <= 8; z++) {
            int depth = 0;
            for (int y = origin.getY(); y >= Math.max(level.getMinY(), origin.getY() - 16); y--) {
                pos.set(origin.getX() + x, y, origin.getZ() + z);
                if (level.getBlockState(pos).is(Blocks.END_STONE)) {
                    if (++depth > 2) level.setBlock(pos, NaturalityBlocks.SMOOTH_ENDSTONE.defaultBlockState(), 2);
                } else depth = 0;
            }
        }
    }
}
