package naturality.worldgen;

import com.mojang.serialization.MapCodec;
import java.util.ArrayList;
import java.util.List;
import naturality.NaturalityBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;

/** Large, grounded rock groups in the bare gaps; validate the entire group before writing. */
public record WildsBouldersFeature() implements Feature {
    public static final MapCodec<WildsBouldersFeature> CODEC=MapCodec.unit(WildsBouldersFeature::new);
    private record Rock(List<BlockPos> blocks,boolean lirestone) {}
    @Override public MapCodec<WildsBouldersFeature> codec() {return CODEC;}

    @Override public boolean place(WorldGenLevel level,ChunkGenerator generator,RandomSource random,BlockPos origin) {
        if (!naturality.config.Feature.WILDS_BOULDERS.enabled()) return false;
        if(!level.getBiome(origin).is(EndWilds.BIOME))return false;
        int radius=4+random.nextInt(3),height=4+random.nextInt(2);
        var noiseGenerator=(net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator)generator;
        var terrain=(EndTerrainDensity.Sampler)level.getLevel().getChunkSource().randomState().getSampler(
            new EndTerrainDensity(noiseGenerator.generatorSettings().value().noiseRouter().finalDensity()));
        boolean merge=random.nextInt(20)==0;
        boolean lirestone=random.nextInt(5)==0;
        var main=plan(level,origin.below(),radius,height,lirestone,random,terrain,merge);
        if(main==null)return false;
        var rocks=new ArrayList<Rock>();rocks.add(main);
        int companions=3+random.nextInt(3);
        double phase=random.nextDouble()*Math.PI*2;
        for(int i=0;i<companions;i++) {
            double angle=phase+i*Math.PI*2/companions;
            int distance=radius+9+random.nextInt(4);
            int x=origin.getX()+(int)Math.round(Math.cos(angle)*distance);
            int z=origin.getZ()+(int)Math.round(Math.sin(angle)*distance);
            var probe=new BlockPos(x,origin.getY(),z);
            if(!writable(level,probe))continue;
            int y=level.getHeight(Heightmap.Types.WORLD_SURFACE_WG,x,z)-1;
            if(Math.abs(y-(origin.getY()-1))>8)continue;
            var small=plan(level,new BlockPos(x,y,z),2+random.nextInt(2),2+random.nextInt(2),random.nextInt(5)==0,random,terrain,merge);
            if(small!=null && (merge || spaced(small,rocks)))rocks.add(small);
        }
        if(rocks.size()<2)return false;
        for(var rock:rocks)for(var pos:rock.blocks()) {
            var existing=level.getBlockState(pos);
            var material=rock.lirestone()
                ? (existing.is(NaturalityBlocks.SMOOTH_ENDSTONE) || existing.is(NaturalityBlocks.SMOOTH_LIRESTONE)
                    ? NaturalityBlocks.SMOOTH_LIRESTONE:NaturalityBlocks.LIRESTONE)
                : (existing.is(NaturalityBlocks.SMOOTH_ENDSTONE)?NaturalityBlocks.SMOOTH_ENDSTONE:Blocks.END_STONE);
            level.setBlock(pos,material.defaultBlockState(),2);
        }
        return true;
    }

    private static Rock plan(WorldGenLevel level,BlockPos base,int radius,int height,boolean lirestone,RandomSource random,
            EndTerrainDensity.Sampler terrain,boolean merge) {
        var blocks=new ArrayList<BlockPos>();
        var sampling=net.minecraft.world.level.levelgen.densityfunction.SamplerContext.builder().build();
        double major=radius*(1.15+random.nextDouble()*.55),minor=radius*(.65+random.nextDouble()*.3);
        double angle=random.nextDouble()*Math.PI,cos=Math.cos(angle),sin=Math.sin(angle);
        int extent=(int)Math.ceil(major);
        for(int z=-extent;z<=extent;z++)for(int x=-extent;x<=extent;x++) {
            double u=x*cos+z*sin,v=-x*sin+z*cos;
            double horizontal=u*u/(major*major)+v*v/(minor*minor);
            if(horizontal>1)continue;
            var column=base.offset(x,0,z);
            if(!writable(level,column) || !wilds(level,column))return null;
            int groundY=level.getHeight(Heightmap.Types.WORLD_SURFACE_WG,column.getX(),column.getZ())-1;
            if(Math.abs(groundY-base.getY())>6) { if(horizontal>.55)continue;return null; }
            var ground=column.atY(groundY);
            if(!merge && level instanceof net.minecraft.server.level.WorldGenRegion) {
                // Distinguish already generated boulders from the underlying natural terrain.
                for(int dz=-3;dz<=3;dz++)for(int dx=-3;dx<=3;dx++) {
                    var nearby=ground.offset(dx,0,dz);
                    if(!writable(level,nearby))return null;
                    int top=level.getHeight(Heightmap.Types.WORLD_SURFACE_WG,nearby.getX(),nearby.getZ())-1;
                    if(LirestoneBoulderFeature.isEndStone(level.getBlockState(nearby.atY(top)))
                        && terrain.sampleValue(sampling,nearby.getX(),top,nearby.getZ())<=0)return null;
                }
            }
            if(!LirestoneBoulderFeature.isEndStone(level.getBlockState(ground))) {
                if(horizontal>.55)continue;return null;
            }
            // Ease the exposed rise down to each column's own terrain height.
            // This creates a low shoulder instead of a vertical wall on downhill sides.
            double t=Math.max(0,Math.min(1,(Math.sqrt(horizontal)-.32)/.68));
            double blend=1-t*t*(3-2*t);
            int topY=groundY+(int)Math.floor(Math.max(0,base.getY()+height-groundY)*blend+.15);
            for(int y=groundY;y<=topY;y++) {
                var pos=column.atY(y);
                if(level.isOutsideBuildHeight(pos) || !writable(level,pos))return null;
                var state=level.getBlockState(pos);
                if(!state.isAir() && !LirestoneBoulderFeature.isEndStone(state))return null;
                blocks.add(pos);
            }
        }
        // Clip tiny final tiers, leaving a broad crown instead of a one-block tip.
        while(!blocks.isEmpty()) {
            int top=blocks.stream().mapToInt(BlockPos::getY).max().orElse(base.getY());
            if(top<=base.getY() || blocks.stream().filter(p->p.getY()==top).count()>=4)break;
            blocks.removeIf(p->p.getY()==top);
        }
        return blocks.isEmpty()?null:new Rock(blocks,lirestone);
    }

    private static boolean spaced(Rock candidate,List<Rock> others) {
        var occupied=new java.util.HashSet<Long>();
        for(var rock:others)for(var pos:rock.blocks())occupied.add(BlockPos.asLong(pos.getX(),0,pos.getZ()));
        for(var pos:candidate.blocks())for(int dz=-3;dz<=3;dz++)for(int dx=-3;dx<=3;dx++)
            if(occupied.contains(BlockPos.asLong(pos.getX()+dx,0,pos.getZ()+dz)))return false;
        return true;
    }

    private static boolean wilds(WorldGenLevel level,BlockPos pos) {
        var resolver=level.getLevel().uncachedBiomeResolver();
        int x=net.minecraft.core.QuartPos.fromBlock(pos.getX()-2),z=net.minecraft.core.QuartPos.fromBlock(pos.getZ()-2);
        for(int dz=0;dz<2;dz++)for(int dx=0;dx<2;dx++)
            if(!resolver.getNoiseBiome(x+dx,0,z+dz).is(EndWilds.BIOME))return false;
        return true;
    }
    private static boolean writable(WorldGenLevel level,BlockPos pos) {
        return (!(level instanceof net.minecraft.server.level.WorldGenRegion region) || region.isWithinWriteZone(pos))
            && level.ensureCanWrite(pos);
    }
}
