package naturality.worldgen;

import naturality.Naturality;
import net.fabricmc.fabric.api.biome.v1.TheEndBiomes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;

public final class LirestoneCrags {
    public static final ResourceKey<Biome> BIOME=ResourceKey.create(Registries.BIOME,Naturality.id("lirestone_crags"));
    private LirestoneCrags() {}

    public static void initialize() {
        if (!naturality.config.Feature.LIRESTONE_CRAGS.enabled()) return;
        TheEndBiomes.addHighlandsBiome(BIOME,1.5);
        TheEndBiomes.addSmallIslandsBiome(BIOME,1.5);
        TheEndBiomes.addMidlandsBiome(BIOME,BIOME,1);
        TheEndBiomes.addBarrensBiome(BIOME,BIOME,1);
    }

    /** All four horizontal biome cells that Minecraft's biome zoom can select for this column. */
    public static boolean[] columns(BiomeManager biomes, int minX, int minZ) {
        boolean[] halo=new boolean[36],result=new boolean[256];
        int qx=QuartPos.fromBlock(minX)-1,qz=QuartPos.fromBlock(minZ)-1;
        for(int z=0;z<6;z++)for(int x=0;x<6;x++)
            halo[x+z*6]=biomes.getNoiseBiomeAtQuart(qx+x,0,qz+z).is(BIOME);
        for(int z=0;z<16;z++)for(int x=0;x<16;x++) {
            int at=((x-2)>>2)+1+(((z-2)>>2)+1)*6;
            result[x+z*16]=halo[at] || halo[at+1] || halo[at+6] || halo[at+7];
        }
        return result;
    }

    /** A coherent, uneven fringe outside the biome, hiding the abrupt material cutoff. */
    public static boolean[] conversionColumns(BiomeManager biomes, int minX, int minZ, long seed) {
        return conversionColumns((net.minecraft.world.level.biome.BiomeResolver)biomes::getNoiseBiomeAtQuart,minX,minZ,seed);
    }

    public static boolean[] conversionColumns(net.minecraft.world.level.biome.BiomeResolver biomes,
            int minX, int minZ, long seed) {
        boolean[] result=new boolean[256];
        var random=net.minecraft.util.RandomSource.create(seed ^ 0x4352414753454447L);
        var broad=new net.minecraft.world.level.levelgen.synth.SimplexNoise(random);
        var detail=new net.minecraft.world.level.levelgen.synth.SimplexNoise(random);
        // Cache the complete fringe halo once; all samples use absolute world coordinates.
        int size=26,qx=QuartPos.fromBlock(minX)-11,qz=QuartPos.fromBlock(minZ)-11;
        boolean[] halo=new boolean[size*size];
        boolean any=false;
        for(int z=0;z<size;z++)for(int x=0;x<size;x++) {
            boolean crags=biomes.getNoiseBiome(qx+x,0,qz+z).is(BIOME);
            halo[x+z*size]=crags;any|=crags;
        }
        if(!any)return result;
        for(int z=0;z<16;z++)for(int x=0;x<16;x++) {
            int wx=minX+x,wz=minZ+z;
            int baseX=QuartPos.fromBlock(wx-2)-qx,baseZ=QuartPos.fromBlock(wz-2)-qz;
            int at=baseX+baseZ*size;
            if(halo[at] || halo[at+1] || halo[at+size] || halo[at+size+1]) {
                result[x+z*16]=true;continue;
            }
            double reach=22+11*broad.get(wx/38.0,wz/38.0)+5*detail.get(wx/11.0,wz/11.0);
            // A circular search rather than a square expansion avoids axis-aligned seams.
            int cx=QuartPos.fromBlock(wx)-qx,cz=QuartPos.fromBlock(wz)-qz;
            int radius=(int)Math.ceil(reach/4);
            search: for(int dz=-radius;dz<=radius;dz++)for(int dx=-radius;dx<=radius;dx++) {
                if((dx*dx+dz*dz)*16>reach*reach)continue;
                if(halo[cx+dx+(cz+dz)*size]) {result[x+z*16]=true;break search;}
            }
        }
        return result;
    }

    /** Exclude the complete tree footprint, including crowns and the sprouts beside its root. */
    public static boolean near(LevelReader level, BlockPos root, int radius) {
        int minX=QuartPos.fromBlock(root.getX()-radius-2),maxX=QuartPos.fromBlock(root.getX()+radius+2);
        int minZ=QuartPos.fromBlock(root.getZ()-radius-2),maxZ=QuartPos.fromBlock(root.getZ()+radius+2);
        for(int z=minZ;z<=maxZ;z++)for(int x=minX;x<=maxX;x++) {
            var biome=level.getNoiseBiome(x,0,z);
            if(biome.is(BIOME) || biome.is(EndWilds.BIOME))return true;
        }
        return false;
    }
}
