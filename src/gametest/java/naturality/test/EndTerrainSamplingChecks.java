package naturality.test;

import naturality.worldgen.EndIslandMaskCache;
import naturality.worldgen.EndTerrainDensity;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.densityfunction.*;

/** Sampling contracts that prevent the gateway blend from rebuilding a noise volume for every voxel. */
final class EndTerrainSamplingChecks {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static final class CountingSource implements DensitySampler {
        int points, volumes;
        final boolean islandGrid;
        CountingSource(boolean islandGrid) { this.islandGrid = islandGrid; }
        float value(int x, int y, int z) {
            return islandGrid ? 13 * (x / 8) + 7 * (z / 8) : (float)(x * .0001 + y * .01 + z * .0002);
        }
        @Override public float sampleValue(SamplerContext context, int x, int y, int z) {
            points++;
            return value(x, y, z);
        }
        @Override public void sampleVolume(SamplerContext context, DensityBuffer output, DensityVolume volume) {
            volumes++;
            for (int z=0;z<volume.sizeZ();z++) for (int x=0;x<volume.sizeX();x++) for (int y=0;y<volume.sizeY();y++)
                output.set(volume.indexUnchecked(x,y,z), value(volume.blockX(x),volume.blockY(y),volume.blockZ(z)));
        }
    }

    static void run() {
        var context=SamplerContext.builder().build();
        var original=new CountingSource(false);
        var terrain=new EndTerrainDensity.Sampler(original,RandomSource.create(1));
        DensityVolume[] volumes={
            new DensityVolume(16,256,16,1016,0,0),
            new DensityVolume(16,256,16,1268,0,0),
            new DensityVolume(16,256,16,-1032,0,-8),
            new DensityVolume(8,8,8,-4,60,-4),
            // All corners are outer, but the volume also samples the protected center.
            new DensityVolume(3,5,3,-1536,0,-1536,1536,48,1536),
            new DensityVolume(16,256,16,1800,0,1800)
        };
        for(int test=0;test<volumes.length;test++) {
            original.points=0; original.volumes=0;
            DensityVolume volume=volumes[test];
            try(var output=context.acquireBuffer(volume)) {
                terrain.sampleVolume(context,output,volume);
                check(original.points==0,"Bulk terrain recursively invokes scalar vanilla noise");
                check(original.volumes==(test==volumes.length-1 ? 0 : 1),"Bulk terrain repeats the vanilla noise graph");
                for(int z=0;z<volume.sizeZ();z+=Math.max(1,volume.sizeZ()/4))
                    for(int x=0;x<volume.sizeX();x+=Math.max(1,volume.sizeX()/4))
                        for(int y=0;y<volume.sizeY();y+=Math.max(1,volume.sizeY()/8))
                            check(Float.floatToIntBits(output.get(volume.indexUnchecked(x,y,z)))
                                ==Float.floatToIntBits(terrain.sampleValue(context,volume.blockX(x),volume.blockY(y),volume.blockZ(z))),
                                "Bulk/scalar terrain mismatch at a protected or transition boundary");
            }
        }

        var islandSource=new CountingSource(true);
        var cache=new EndIslandMaskCache(islandSource);
        for(int x=-7;x<=7;x++)for(int z=-7;z<=7;z++)
            check(cache.sampleValue(context,x,100,z)==islandSource.value(x,0,z),"Island cache changed negative-coordinate truncation");
        check(islandSource.points==1,"Equivalent positions repeat the 625-neighbor island search");
        var volume=new DensityVolume(33,5,33,-16,0,-16,1,50,1);
        try(var output=context.acquireBuffer(volume)) {
            cache.sampleVolume(context,output,volume);
            check(islandSource.points==25 && islandSource.volumes==0,"Bulk island sampling bypasses the coarse-grid cache");
            for(int z=0;z<33;z++)for(int x=0;x<33;x++)for(int y=0;y<5;y++)
                check(output.get(volume.indexUnchecked(x,y,z))==islandSource.value(volume.blockX(x),volume.blockY(y),volume.blockZ(z)),
                    "Cached island density differs across a cell edge or Y level");
        }
    }
}
