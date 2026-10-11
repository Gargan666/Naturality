package naturality.chorus;

import naturality.NaturalityBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;

/** Small groups on the ground immediately surrounding a completed worldgen plant. */
public final class ChorusSprouts {
    private static final int[] HEIGHTS = {0,-1,1,-2,2};
    private ChorusSprouts() {}

    public static int scatter(LevelAccessor level, BlockPos root, RandomSource random) {
        if (!naturality.config.Feature.CHORUS_SPROUTS.enabled()) return 0;
        if (!level.getBlockState(root).is(Blocks.CHORUS_PLANT)) return 0;
        int existing=0;
        int[] sites=new int[24];
        int count=0;
        for(int cell=0;cell<25;cell++) {
            if(cell==12)continue;
            sites[count++]=cell;
            int x=root.getX()+cell%5-2,z=root.getZ()+cell/5-2;
            if(level.getChunkForCollisions(x>>4,z>>4)==null)continue;
            for(int height:HEIGHTS)
                if(level.getBlockState(new BlockPos(x,root.getY()+height,z)).is(NaturalityBlocks.CHORUS_SPROUT))existing++;
        }
        if(existing>=4)return 0;
        int wanted=Math.min(4-existing,1+random.nextInt(4)),placed=0;
        var sprout=NaturalityBlocks.CHORUS_SPROUT.defaultBlockState();
        // Visit each of the 24 nearby columns at most once, in a shuffled order.
        for(int index=0;index<sites.length && placed<wanted;index++) {
            int choice=index+random.nextInt(sites.length-index);
            int cell=sites[choice];sites[choice]=sites[index];sites[index]=cell;
            int x=root.getX()+cell%5-2,z=root.getZ()+cell/5-2;
            if(level.getChunkForCollisions(x>>4,z>>4)==null)continue;
            for(int height:HEIGHTS) {
                BlockPos pos=new BlockPos(x,root.getY()+height,z);
                if(level.isOutsideBuildHeight(pos) || !level.isEmptyBlock(pos) || !sprout.canSurvive(level,pos))continue;
                if(level instanceof WorldGenLevel worldgen && !worldgen.ensureCanWrite(pos))continue;
                if(level.setBlock(pos,sprout,2)){placed++;break;}
            }
        }
        return placed;
    }
}
