package naturality.worldgen;

import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;

/** Coherent surface patches, independent of chunk boundaries and generation order. */
public final class LyrenHeathPatches {
    private final SimplexNoise body;
    private final SimplexNoise edge;
    private final SimplexNoise warp;

    public LyrenHeathPatches(long seed) {
        var random=RandomSource.create(seed ^ 0x4c5952454e484541L);
        body=new SimplexNoise(random);
        edge=new SimplexNoise(random);
        warp=new SimplexNoise(random);
    }

    public boolean contains(int x,int z) {
        double wx=x+7*warp.get(x/17.0,z/17.0);
        double wz=z+7*warp.get(x/17.0+47,z/17.0-31);
        return body.get(wx/24.0,wz/24.0)+.36*edge.get(x/5.0,z/5.0)
            +.13*warp.get(x/2.5,z/2.5)>.15;
    }
}
