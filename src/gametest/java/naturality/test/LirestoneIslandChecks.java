package naturality.test;

import naturality.NaturalityBlocks;
import naturality.worldgen.EndTerrainDensity;
import naturality.worldgen.LirestonePatches;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.densityfunction.DensityVolume;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;

/** Verify complete islands using their real density, including blocks on the other side of chunk borders. */
final class LirestoneIslandChecks {
    static BlockPos run(ServerLevel end, EndTerrainDensity.Sampler terrain, LirestonePatches patches) {
        EndTerrainDensity.Sampler.SmallIsland selected=null;
        search: for(int x=-8192;x<8192;x+=32)for(int z=-8192;z<8192;z+=32) {
            var candidate=terrain.surfaceMaterials(x,z).smallIsland();
            if(candidate!=null && patches.converts(candidate)) {selected=candidate;break search;}
        }
        if(selected==null)throw new AssertionError("No rare Lirestone island found");
        var volume=new DensityVolume(selected.maxX()-selected.minX()+1,selected.maxY()-selected.minY()+1,
            selected.maxZ()-selected.minZ()+1,selected.minX(),selected.minY(),selected.minZ());
        var context=SamplerContext.builder().build();
        var mask=new java.util.BitSet(volume.sizeX()*volume.sizeY()*volume.sizeZ());
        var chunks=new java.util.HashSet<Long>();
        int count=0,highest=0,minX=Integer.MAX_VALUE,maxX=Integer.MIN_VALUE,minZ=Integer.MAX_VALUE,maxZ=Integer.MIN_VALUE;
        // Request the farther chunks first, so conversion cannot depend on which side was generated first.
        for(int z=selected.maxZ()>>4;z>=selected.minZ()>>4;z--)
            for(int x=selected.maxX()>>4;x>=selected.minX()>>4;x--)end.getChunk(x,z);
        try(var buffer=context.acquireBuffer(volume)) {
            terrain.sampleVolume(context,buffer,volume);
            for(int z=0;z<volume.sizeZ();z++)for(int x=0;x<volume.sizeX();x++) {
                int wx=volume.blockX(x),wz=volume.blockZ(z);
                var materials=terrain.surfaceMaterials(wx,wz);
                if(!selected.equals(materials.smallIsland()))continue;
                for(int y=0;y<volume.sizeY();y++) {
                    int wy=volume.blockY(y);
                    if(!materials.inSmallIsland(wy) || buffer.get(volume.indexUnchecked(x,y,z))<=0)continue;
                    if(!end.getBlockState(new BlockPos(wx,wy,wz)).is(NaturalityBlocks.LIRESTONE))
                        throw new AssertionError("Whole Lirestone island has an unconverted block at "+wx+","+wy+","+wz);
                    mask.set(volume.indexUnchecked(x,y,z));count++;highest=Math.max(highest,wy);
                    minX=Math.min(minX,wx);maxX=Math.max(maxX,wx);minZ=Math.min(minZ,wz);maxZ=Math.max(maxZ,wz);
                    chunks.add(((long)(wx>>4)<<32)^((wz>>4)&0xffffffffL));
                }
            }
            // The converted body must not be attached to unconverted natural terrain.
            for(int index=mask.nextSetBit(0);index>=0;index=mask.nextSetBit(index+1)) {
                int y=index%volume.sizeY(),column=index/volume.sizeY(),x=column%volume.sizeX(),z=column/volume.sizeX();
                for(var direction:net.minecraft.core.Direction.values()) {
                    int nx=x+direction.getStepX(),ny=y+direction.getStepY(),nz=z+direction.getStepZ();
                    if(nx<0 || ny<0 || nz<0 || nx>=volume.sizeX() || ny>=volume.sizeY() || nz>=volume.sizeZ())continue;
                    int next=volume.indexUnchecked(nx,ny,nz);
                    if(!mask.get(next) && buffer.get(next)>0)
                        throw new AssertionError("Lirestone outcrop is attached to a larger island");
                }
            }
        }
        if(count<500 || count>150000 || chunks.size()<2)throw new AssertionError("Selected island is missing, too large or does not cross chunks");
        System.out.println("Entire Lirestone island: "+count+" blocks across "+chunks.size()+" chunks, bounds "+minX+".."+maxX+", "+minZ+".."+maxZ);
        return new BlockPos((minX+maxX)/2,highest,(minZ+maxZ)/2);
    }
}
