package naturality.client.particle;

import java.util.HashMap;
import java.util.Map;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import naturality.NaturalityParticles;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Shared, bounded client-thread visibility decisions. Never uses collision shapes as opacity. */
public final class ParticleVisibility {
    public interface Access { boolean naturality$hidden(boolean spawning); }
    private record Region(int x,int y,int z,int X,int Y,int Z,boolean water) { }
    private static final int MAX_TESTS=256,MAX_BLOCK_QUERIES=16384;
    private static final Map<Region,Boolean> REGIONS=new HashMap<>();
    private static final Long2ByteOpenHashMap SOLID=new Long2ByteOpenHashMap();
    private static final Long2ByteOpenHashMap WATER=new Long2ByteOpenHashMap();
    private static @org.jspecify.annotations.Nullable ClientLevel world;
    private static Vec3 eye=Vec3.ZERO;
    private static long tick=Long.MIN_VALUE;
    private static int tests,queries,rejected,removed;
    private static final ClassValue<Boolean> STANDARD_QUADS=new ClassValue<>() {
        @Override protected Boolean computeValue(@org.jspecify.annotations.Nullable Class<?> type) {
            if(type==null)return false;
            try {
                var renderer=type.getMethod("extract",QuadParticleRenderState.class,Camera.class,float.class).getDeclaringClass();
                return renderer==SingleQuadParticle.class || renderer==WeatherClusterParticle.class;
            } catch(NoSuchMethodException e) { return false; }
        }
    };
    private ParticleVisibility() { }

    public record Stats(int regionTests,int blockQueries,int rejectedSpawns,int removedParticles) { }
    public static Stats stats(){return new Stats(tests,queries,rejected,removed);}
    public static void reset(){world=null;tick=Long.MIN_VALUE;REGIONS.clear();SOLID.clear();WATER.clear();tests=queries=rejected=removed=0;}
    public static void invalidate(ClientLevel level,@org.jspecify.annotations.Nullable BlockPos pos) {
        if(world!=level)return;
        if(!WATER.isEmpty()){WATER.clear();REGIONS.clear();}
        // Preserve the work budget when terrain changes repeatedly in a single tick.
        if(pos==null){REGIONS.clear();SOLID.clear();}
        else if(SOLID.remove(pos.asLong())!=0)REGIONS.clear();
    }
    public static boolean supported(Particle particle){return STANDARD_QUADS.get(particle.getClass());}
    public static void rejected(){rejected++;}
    public static void removed(){removed++;}

    public static boolean fallType(ParticleOptions options) {
        var t=options.getType();
        return t==NaturalityParticles.WATERFALL || t==NaturalityParticles.WATERFALL_BIG
            || t==NaturalityParticles.LAVAFALL || t==NaturalityParticles.LAVAFALL_BIG;
    }
    public static boolean outsideView(double x,double y,double z,double X,double Y,double Z) {
        var camera=Minecraft.getInstance().gameRenderer.mainCamera();
        return camera.isInitialized() && !camera.isPanoramicMode()
            && !camera.getCullFrustum().isVisible(new AABB(x,y,z,X,Y,Z));
    }
    /** Bounds include the later border enlargement, before the provider allocates a particle. */
    public static boolean rejectSpawn(ClientLevel level,ParticleOptions options,double x,double y,double z) {
        var type=options.getType();
        double radius;
        if(type==NaturalityParticles.WATERFALL || type==NaturalityParticles.LAVAFALL)radius=.6;
        else if(type==NaturalityParticles.WATERFALL_BIG || type==NaturalityParticles.LAVAFALL_BIG)radius=1.5;
        else if(type==NaturalityParticles.RAIN_CLUSTER || type==NaturalityParticles.SNOW_CLUSTER)radius=3.4;
        else return false;
        return hidden(level,x-radius,y-radius,z-radius,x+radius,y+radius,z+radius,true,
            type==NaturalityParticles.WATERFALL || type==NaturalityParticles.WATERFALL_BIG);
    }

    public static boolean hidden(ClientLevel level,double x,double y,double z,double X,double Y,double Z,boolean spawning) {
        return hidden(level,x,y,z,X,Y,Z,spawning,false);
    }
    public static boolean hidden(ClientLevel level,double x,double y,double z,double X,double Y,double Z,boolean spawning,boolean water) {
        var client=Minecraft.getInstance();
        if(client.level!=level || client.getCameraEntity()==null)return false;
        var camera=client.gameRenderer.mainCamera().position();
        if(world!=level || tick!=level.getGameTime() || !eye.equals(camera)) {
            reset();world=level;tick=level.getGameTime();eye=camera;
        }
        if(!Double.isFinite(x+y+z+X+Y+Z) || X-x>16 || Y-y>16 || Z-z>16)return false;
        if(outsideView(x,y,z,X,Y,Z))return true;
        int bx=(int)Math.floor(x),by=(int)Math.floor(y),bz=(int)Math.floor(z);
        if(X<bx+1 && Y<by+1 && Z<bz+1
                && !(eye.x>=bx && eye.x<=bx+1 && eye.y>=by && eye.y<=by+1 && eye.z>=bz && eye.z<=bz+1)
                && solid(new BlockPos(bx,by,bz)))return true;
        var region=new Region((int)Math.floor(x),(int)Math.floor(y),(int)Math.floor(z),
            (int)Math.ceil(X),(int)Math.ceil(Y),(int)Math.ceil(Z),water);
        var cached=REGIONS.get(region);
        if(cached!=null)return cached;
        // Rotate existing groups across four ticks; new spawns receive immediate checks.
        int schedule=region.hashCode();
        schedule^=schedule>>>16;
        schedule*=0x7feb352d;
        schedule^=schedule>>>15;
        if(!spawning && (schedule&3)!=(tick&3))return false;
        if(tests>=MAX_TESTS || queries>=MAX_BLOCK_QUERIES)return false;
        tests++;
        boolean hidden=ParticleOcclusion.hidden(eye,new AABB(region.x,region.y,region.z,region.X,region.Y,region.Z),
            pos->solid(pos) || (water && fullWater(pos)));
        REGIONS.put(region,hidden);
        return hidden;
    }

    private static boolean fullWater(BlockPos pos) {
        byte cached=WATER.get(pos.asLong());
        if(cached!=0)return cached==2;
        var level=world;
        if(level==null || queries+2>MAX_BLOCK_QUERIES)return false;
        queries+=2;
        var chunk=level.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4);
        // Shallow surfaces and waterlogged shapes cannot prove full cube coverage.
        boolean full=chunk!=null && chunk.getBlockState(pos).is(Blocks.WATER)
            && chunk.getFluidState(pos.above()).is(net.minecraft.tags.FluidTags.WATER);
        WATER.put(pos.asLong(),(byte)(full?2:1));
        return full;
    }
    private static boolean solid(BlockPos pos) {
        long key=pos.asLong();
        byte cached=SOLID.get(key);
        if(cached!=0)return cached==2;
        var level=world;
        if(level==null || queries>=MAX_BLOCK_QUERIES)return false;
        queries++;
        var chunk=level.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4);
        boolean opaque=false;
        if(chunk!=null) {
            var state=chunk.getBlockState(pos);
            // Snow layers may be rendered below their saved cell; their state-only
            // occlusion shape cannot prove that this cell is visually filled.
            opaque=state.getRenderShape()==net.minecraft.world.level.block.RenderShape.MODEL
                && state.canOcclude() && !state.is(BlockTags.LEAVES) && !state.is(Blocks.SNOW)
                && Block.isShapeFullBlock(state.getOcclusionShape());
        }
        SOLID.put(key,(byte)(opaque?2:1));
        return opaque;
    }
}
