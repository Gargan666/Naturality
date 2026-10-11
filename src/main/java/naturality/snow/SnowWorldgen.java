package naturality.snow;

import naturality.config.GameplaySettings;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
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
            placeUnderCanopy(level,surface);
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

    /** Leaves admit generated snow; solid roofs stop the scan before sheltered surfaces. */
    private static void placeUnderCanopy(WorldGenLevel level,BlockPos surface) {
        boolean canopy=false;
        var cursor=surface.mutable();
        while(level.isInsideBuildHeight(cursor.getY())) {
            var state=level.getBlockState(cursor);
            if(state.is(BlockTags.LEAVES))canopy=true;
            else if(!state.isAir() && !state.is(Blocks.SNOW) && !state.is(BlockTags.LOGS)
                    && !SnowGeometry.isFoliage(state)) {
                if(canopy)placeSnow(level,cursor.above());
                break;
            }
            if(canopy && state.is(BlockTags.LEAVES))placeSnow(level,cursor.above());
            if(canopy && SnowGeometry.isFoliage(state)) {
                var owner=cursor.above();
                int depth=1;
                while(depth<SnowGeometry.MAX_DEPTH && level.isInsideBuildHeight(owner.getY())
                        && SnowGeometry.isFoliage(level.getBlockState(owner))) {
                    owner=owner.above();depth++;
                }
                if(state.getFluidState().isEmpty())placeSnow(level,owner);
            }
            cursor.move(0,-1,0);
        }
    }

    private static void placeSnow(WorldGenLevel level,BlockPos owner) {
        if(!level.isInsideBuildHeight(owner.getY()) || !level.getBlockState(owner).isAir()
                || !level.getBiome(owner).value().shouldSnow(level,owner))return;
        if(!level.setBlock(owner,Blocks.SNOW.defaultBlockState(),2))return;
        // Fitted plant snow can have its ground support several cells below its owner.
        var ground=owner.below();
        int depth=0;
        while(depth<SnowGeometry.MAX_DEPTH && SnowGeometry.isFoliage(level.getBlockState(ground))) {
            ground=ground.below();depth++;
        }
        var support=level.getBlockState(ground);
        if(support.hasProperty(SnowyBlock.SNOWY))level.setBlock(ground,support.setValue(SnowyBlock.SNOWY,true),2);
    }
}
