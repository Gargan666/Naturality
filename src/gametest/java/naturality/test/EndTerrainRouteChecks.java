package naturality.test;

import naturality.worldgen.EndTerrainDensity;
import net.minecraft.world.level.levelgen.densityfunction.DensityVolume;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;

/** Flood actual block-height surfaces using only one-block steps, not the planned ramp geometry. */
final class EndTerrainRouteChecks {
    static void check(EndTerrainDensity.Sampler terrain, int centerX, int centerZ) {
        int size = 320;
        int[] top = new int[size * size];
        java.util.Arrays.fill(top, -1);
        var context = SamplerContext.builder().build();
        var volume = new DensityVolume(size, 64, size, centerX-size/2, 0, centerZ-size/2, 1, 4, 1);
        try (var buffer = context.acquireBuffer(volume)) {
            terrain.sampleVolume(context, buffer, volume);
            for (int z=0; z<size; z++) for (int x=0; x<size; x++) {
                for (int y=63; y>=0; y--) if (buffer.get(volume.indexUnchecked(x,y,z)) > 0) {
                    for (int exact=y*4+3; exact>=y*4; exact--)
                        if (terrain.sampleValue(context,volume.blockX(x),exact,volume.blockZ(z)) > 0) {
                            top[x+z*size]=exact; break;
                        }
                    break;
                }
            }
        }
        boolean[] seen = new boolean[top.length];
        int bestRise=0, traversable=0;
        for (int start=0; start<top.length; start++) {
            if (seen[start] || top[start]<0) continue;
            var queue = new java.util.ArrayDeque<Integer>();
            queue.add(start); seen[start]=true;
            int count=0, low=256, high=0;
            while (!queue.isEmpty()) {
                int at=queue.removeFirst(), x=at%size, z=at/size;
                count++; low=Math.min(low,top[at]); high=Math.max(high,top[at]);
                for (int direction=0; direction<4; direction++) {
                    int nx=x+(direction==0?1:direction==1?-1:0);
                    int nz=z+(direction==2?1:direction==3?-1:0);
                    if(nx<0 || nz<0 || nx>=size || nz>=size)continue;
                    int next=nx+nz*size;
                    if(!seen[next] && top[next]>=0 && Math.abs(top[next]-top[at])<=1) {
                        seen[next]=true; queue.add(next);
                    }
                }
            }
            if(count>=2500) {bestRise=Math.max(bestRise,high-low);traversable=Math.max(traversable,count);}
        }
        System.out.println("Terrain routes at "+centerX+","+centerZ+": walkable area="+traversable+", elevation span="+bestRise);
        if(bestRise<30)throw new AssertionError("Ledges lack a walkable route across elevations: "+bestRise);
    }
}
