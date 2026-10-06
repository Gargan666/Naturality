package naturality.snow;

import naturality.config.GameplaySettings;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowyBlock;
import net.minecraft.world.level.levelgen.Heightmap;

/** Preserve vegetation while giving fitted snow its own saved cell above it. */
public final class SnowWorldgen {
    private SnowWorldgen() {}
    public static void placeOnFoliage(WorldGenLevel level,BlockPos origin) {
        if(!GameplaySettings.snowWrapping(level))return;
        for(int dx=0;dx<16;dx++)for(int dz=0;dz<16;dz++) {
            int x=origin.getX()+dx,z=origin.getZ()+dz;
            var surface=new BlockPos(x,level.getHeight(Heightmap.Types.MOTION_BLOCKING,x,z),z);
            // Solid roofs and trees keep their normal surface snow placement.
            var plant=level.getBlockState(surface);
            if(!SnowGeometry.isFoliage(plant))continue;
            var owner=surface;
            int height=0;
            while(height<SnowGeometry.MAX_DEPTH && level.isInsideBuildHeight(owner.getY())
                    && SnowGeometry.isFoliage(level.getBlockState(owner))) {
                if(!level.getBlockState(owner).getFluidState().isEmpty())break;
                owner=owner.above();height++;
            }
            if(height==0 || !level.isInsideBuildHeight(owner.getY()) || !level.getBlockState(owner).isAir())continue;
            if(!level.getBiome(owner).value().shouldSnow(level,owner))continue;
            if(!level.setBlock(owner,Blocks.SNOW.defaultBlockState(),2))continue;
            var ground=surface.below();var support=level.getBlockState(ground);
            if(support.hasProperty(SnowyBlock.SNOWY))level.setBlock(ground,support.setValue(SnowyBlock.SNOWY,true),2);
        }
    }
}
