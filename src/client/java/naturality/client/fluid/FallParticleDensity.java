package naturality.client.fluid;

/** Shared spawn-density falloff for waterfall and lavafall spray. */
public final class FallParticleDensity {
    public static final int RANGE=72;
    private FallParticleDensity() {}
    public static double multiplier(double distance) {
        double t=Math.clamp((distance-36)/36,0,1);
        return 1-t*t*(3-2*t);
    }
    /** Search the columns closest to the camera first after entering a new area. */
    public static int[] scanOrder(int radius) {
        int width=radius*2+1;
        int[] order=new int[width*width];
        int count=0;
        for(int ring=0;ring<=radius;ring++)for(int z=-ring;z<=ring;z++)for(int x=-ring;x<=ring;x++)
            if(Math.max(Math.abs(x),Math.abs(z))==ring)
                order[count++]=(z+radius)*width+x+radius;
        return order;
    }
    public static float smallFallSize(net.minecraft.client.multiplayer.ClientLevel level,
                                     net.minecraft.core.BlockPos start,boolean lava) {
        var tag=lava?net.minecraft.tags.FluidTags.LAVA:net.minecraft.tags.FluidTags.WATER;
        if(level.getFluidState(start).is(tag) && level.getFluidState(start).isSource())start=start.above();
        int flowing=0;
        for(int dy=0;dy<10;dy++) {
            var fluid=level.getFluidState(start.above(dy));
            if(!fluid.is(tag) || fluid.isSource())break;
            flowing++;
        }
        return (float)Math.clamp(.35+flowing*.08,.35,1.0);
    }
}
