package naturality.worldgen;

import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;

/** Continuous deposits in world coordinates, independent of chunk borders or generation order. */
public final class LirestonePatches {
    private final SimplexNoise shape;
    private final SimplexNoise edge;
    private final SimplexNoise regions;
    private final java.util.Map<EndTerrainDensity.Sampler.SmallIsland,Boolean> islands = new java.util.HashMap<>();

    public LirestonePatches(long seed) {
        var random = RandomSource.create(seed ^ 0x4c49524553544f4eL);
        shape = new SimplexNoise(random);
        edge = new SimplexNoise(random);
        regions = new SimplexNoise(random);
    }

    /** Broad areas have no deposits at all, with a smooth increase toward richer regions. */
    public double regionalAbundance(int x, int z) {
        return EndTerrainDensity.ease((regions.get(x / 1600.0, z / 1600.0) + .2) / .6);
    }

    public boolean contains(int x, int y, int z) {
        return contains(x, y, z, 1, regionalAbundance(x, z));
    }

    public boolean contains(int x, int y, int z, int depth, double abundance) {
        return contains(x,y,z,depth,abundance,1);
    }

    public boolean contains(int x, int y, int z, int depth, double abundance, double rimAffinity) {
        if (abundance == 0) return false;
        // Favor deposits near each ledge's upper surface. Deep cliff faces and undersides
        // retain only occasional deposits, rather than exposing veins all the way down.
        double burial = EndTerrainDensity.ease((depth - 3) / 12.0);
        double threshold = .56 + .7 * (1 - abundance) + .32 * burial + .34 * (1-rimAffinity);
        double body = shape.get(x / 28.0, y / 21.0, z / 28.0);
        // Only evaluate fine detail near the deposit boundary.
        if (body < threshold - .22) return false;
        if (body > threshold + .22) return true;
        return body + .22 * edge.get(x / 9.0, y / 8.0, z / 9.0) > threshold;
    }

    public boolean converts(EndTerrainDensity.Sampler.SmallIsland island) {
        if (island==null) return false;
        Boolean saved=islands.get(island);
        if(saved!=null)return saved;
        boolean allowed=regionalAbundance((island.minX()+island.maxX())/2,(island.minZ()+island.maxZ())/2)>0;
        // Make one decision for the complete island, including portions in neighboring chunks.
        // Entirely Lirestone islands are excluded wherever the regional field has zero abundance.
        for(int z=island.minZ();z<=island.maxZ() && allowed;z++)
            for(int x=island.minX();x<=island.maxX();x++)
                if(regionalAbundance(x,z)==0){allowed=false;break;}
        islands.put(island,allowed);
        return allowed;
    }
}
