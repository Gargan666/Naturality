package naturality.client.fluid;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;

/** Shared visible water geometry for model rims and impacts. Coordinates within a tile are in blocks. */
public record WaterSurface(BlockPos pos,double nw,double sw,double se,double ne,boolean cauldron) {
    public double height(double x,double z) {
        return pos.getY()+(z>=x ? nw+(se-sw)*x+(sw-nw)*z : nw+(ne-nw)*x+(se-ne)*z)-(cauldron?0:0.001);
    }
    public boolean contains(double x,double z) {
        double inset=cauldron?2.0/16:0;
        return x>=inset && z>=inset && x<1-inset && z<1-inset;
    }
    public static @org.jspecify.annotations.Nullable WaterSurface at(ClientLevel level,BlockPos p) {
        if(!naturality.util.LoadedChunks.has(level, p)) return null;
        var state=level.getBlockState(p);
        if(state.is(Blocks.WATER_CAULDRON)) {
            double h=(6+3*state.getValue(LayeredCauldronBlock.LEVEL))/16.0;
            return new WaterSurface(p.immutable(),h,h,h,h,true);
        }
        if(!level.getFluidState(p).is(FluidTags.WATER) || level.getFluidState(p.above()).is(FluidTags.WATER)
            || state.isSolidRender() || level.getBlockState(p.above()).isSolidRender()) return null;
        double own=fluidHeight(level,p),n=fluidHeight(level,p.north()),s=fluidHeight(level,p.south()),
            w=fluidHeight(level,p.west()),e=fluidHeight(level,p.east());
        return new WaterSurface(p.immutable(),corner(level,p.north().west(),own,n,w),corner(level,p.south().west(),own,s,w),
            corner(level,p.south().east(),own,s,e),corner(level,p.north().east(),own,n,e),false);
    }
    private static double corner(ClientLevel level,BlockPos diagonal,double own,double a,double b) {
        if(own>=1 || a>=1 || b>=1) return 1;
        double c=a>0 || b>0?fluidHeight(level,diagonal):-1;
        if(c>=1) return 1;
        double sum=0,weight=0;
        for(double h:new double[]{own,a,b,c}) if(h>=0) {double w=h>=0.8?10:1;sum+=h*w;weight+=w;}
        return sum/weight;
    }
    @SuppressWarnings("deprecation") // Matches vanilla FluidRenderer corner heights, including partial supports.
    private static double fluidHeight(ClientLevel level,BlockPos p) {
        var fluid=level.getFluidState(p);
        return fluid.is(FluidTags.WATER)?(level.getFluidState(p.above()).is(FluidTags.WATER)?1:fluid.getOwnHeight())
            :level.getBlockState(p).isSolid()?-1:0;
    }
}
