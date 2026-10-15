package naturality.worldgen;

import com.mojang.serialization.MapCodec;
import java.util.LinkedHashMap;
import java.util.Map;
import naturality.Naturality;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

/** A contained mountain lake, a low spillway, and a fully planned downstream water path. */
public record MountainWaterfallFeature() implements Feature {
    public static final MapCodec<MountainWaterfallFeature> CODEC=MapCodec.unit(MountainWaterfallFeature::new);
    public static final ResourceKey<PlacedFeature> PLACED=ResourceKey.create(Registries.PLACED_FEATURE,Naturality.id("mountain_waterfall"));

    public static void initialize() {
        Registry.register(BuiltInRegistries.FEATURE_TYPE,Naturality.id("mountain_waterfall"),CODEC);
        if(naturality.config.Feature.MOUNTAIN_WATERFALLS.enabled())
            BiomeModifications.addFeature(BiomeSelectors.foundInOverworld(),GenerationStep.Decoration.LAKES,PLACED);
    }
    @Override public MapCodec<MountainWaterfallFeature> codec() {return CODEC;}

    @Override public boolean place(WorldGenLevel level,ChunkGenerator generator,RandomSource random,BlockPos origin) {
        if(!naturality.config.Feature.MOUNTAIN_WATERFALLS.enabled() || level.getLevel().dimension()!=Level.OVERWORLD)return false;
        // Center the footprint in the decorating chunk, leaving room in its writable neighbours.
        int x=(origin.getX()&~15)+8,z=(origin.getZ()&~15)+8;
        int ground=level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG,x,z)-1;
        if(ground<level.getSeaLevel()+35)return false;
        var center=new BlockPos(x,ground-1,z);
        if(!level.getBiome(center).is(MOUNTAIN_BIOMES))return false;
        int radius=7+random.nextInt(4),width=3+2*random.nextInt(2);
        int first=random.nextInt(4);
        for(int i=0;i<4;i++) {
            var direction=Direction.from2DDataValue((first+i)&3);
            for(int edge=radius;edge<=radius+4;edge++) {
                var lip=center.relative(direction,edge);
                if(!writable(level,lip))continue;
                int bottom=level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG,lip.getX(),lip.getZ())-1;
                if(center.getY()-bottom<18 || center.getY()-bottom>96)continue;
                var plan=plan(level,center,radius,width,direction,edge);
                if(plan==null)continue;
                // Flag 2 retains WorldGenRegion's native fluid post-processing on promotion.
                for(var entry:plan.entrySet())level.setBlock(entry.getKey(),entry.getValue(),2);
                // Saved fluid ticks retain ordinary water behavior after the already-filled flow loads.
                for(var entry:plan.entrySet())if(entry.getValue().is(Blocks.WATER))
                    level.scheduleTick(entry.getKey(),entry.getValue().getFluidState().getType(),5);
                return true;
            }
        }
        return false;
    }

    /** Exposed for the integration test's controlled ledges; no blocks change on rejection. */
    public static Map<BlockPos,BlockState> plan(WorldGenLevel level,BlockPos center,int radius,int width,Direction direction,int edge) {
        var edits=new LinkedHashMap<BlockPos,BlockState>();
        var across=direction.getClockWise();
        int half=width/2;
        // Sink the lake enough to retain the lowest dry bank on uneven mountain ledges.
        int waterY=center.getY();
        for(int u=-radius-2;u<=radius+2;u++)for(int v=-radius-2;v<=radius+2;v++) {
            double shape=shoreline(u,v,radius);
            if(shape<.8 || shape>1.32 || (u>=radius-2 && Math.abs(v)<=half))continue;
            var bank=center.relative(direction,u).relative(across,v);
            if(!writable(level,bank))return null;
            waterY=Math.min(waterY,level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG,bank.getX(),bank.getZ())-2);
        }
        if(center.getY()-waterY>6)return null;
        center=center.atY(waterY);
        var lip=center.relative(direction,edge);
        if(!writable(level,lip))return null;
        int drop=center.getY()-(level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG,lip.getX(),lip.getZ())-1);
        if(drop<18 || drop>96)return null;
        // Mild asymmetric shoreline; everything outside the outlet must retain a dry rim.
        for(int u=-radius-2;u<=radius+2;u++)for(int v=-radius-2;v<=radius+2;v++) {
            double shape=shoreline(u,v,radius);
            var column=center.relative(direction,u).relative(across,v);
            if(!writable(level,column))return null;
            boolean outlet=u>=radius-2 && Math.abs(v)<=half;
            if(shape>1.32)continue;
            int ground=level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG,column.getX(),column.getZ())-1;
            if(!outlet && (ground<center.getY()+1 || ground>center.getY()+12))return null;
            if(shape>1 && !outlet)continue;
            if(outlet && ground<center.getY()-1)continue;
            int depth=1+(int)Math.floor(3*Math.max(0,1-shape));
            if(!natural(level.getBlockState(column.below(depth))))return null;
            for(int y=center.getY()-depth+1;y<=Math.max(ground,center.getY()+2);y++) {
                var pos=column.atY(y);
                if(!editable(level,pos))return null;
                edits.put(pos,y<=center.getY()?Blocks.WATER.defaultBlockState():Blocks.AIR.defaultBlockState());
            }
        }
        // Cut the low opening through the cliff's rim. Its bed is left in place.
        for(int u=radius-2;u<=edge;u++)for(int v=-half;v<=half;v++) {
            var column=center.relative(direction,u).relative(across,v);
            if(!writable(level,column))return null;
            int ground=level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG,column.getX(),column.getZ())-1;
            if(ground>center.getY()+12)return null;
            for(int y=center.getY()-1;y<=Math.max(center.getY()+2,ground);y++) {
                var pos=column.atY(y);
                if(!editable(level,pos))return null;
                // Only the lake is a reservoir. The opening and fall use flowing water.
                edits.put(pos,Blocks.AIR.defaultBlockState());
            }
            // In freezing mountains the spillway stays open beneath the lake's ice lid.
            if(ground>=center.getY()-1 && freezing(level,column))edits.put(column,Blocks.ICE.defaultBlockState());
        }
        for(var entry:edits.entrySet()) {
            var pos=entry.getKey();
            if(entry.getValue().is(Blocks.WATER) && edits.getOrDefault(pos.above(),level.getBlockState(pos.above())).isAir()
                    && freezing(level,pos))entry.setValue(Blocks.ICE.defaultBlockState());
        }
        return WaterfallFlowPlanner.fill(level,center,edits)?edits:null;
    }

    static boolean writable(WorldGenLevel level,BlockPos pos) {
        return level.isInsideBuildHeight(pos.getY())
            && (!(level instanceof net.minecraft.server.level.WorldGenRegion region) || region.isWithinWriteZone(pos))
            && level.ensureCanWrite(pos);
    }
    static boolean natural(BlockState state) {
        return state.is(BlockTags.BASE_STONE_OVERWORLD) || state.is(BlockTags.DIRT)
            || state.is(Blocks.GRAVEL) || state.is(Blocks.SNOW_BLOCK);
    }
    static boolean editable(WorldGenLevel level,BlockPos pos) {
        if(!writable(level,pos))return false;
        var state=level.getBlockState(pos);
        return !state.hasBlockEntity() && (natural(state) || state.isAir()
            || naturality.snow.SnowGeometry.isFoliage(state) || state.is(Blocks.SNOW) || state.is(Blocks.POWDER_SNOW));
    }

    private static double shoreline(int u,int v,int radius) {
        double angle=Math.atan2(v,u);
        return (u*u/(double)(radius*radius)+v*v/(double)((radius-1)*(radius-1)))
            *(1+.07*Math.sin(angle*3)+.04*Math.cos(angle*5));
    }
    private static boolean freezing(WorldGenLevel level,BlockPos pos) {
        return level.getBiome(pos).value().coldEnoughToSnow(pos,level.getSeaLevel())
            && level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK,pos)<10;
    }
    private static final net.minecraft.tags.TagKey<net.minecraft.world.level.biome.Biome> MOUNTAIN_BIOMES=
        net.minecraft.tags.TagKey.create(Registries.BIOME,Naturality.id("mountain_waterfalls"));
}
