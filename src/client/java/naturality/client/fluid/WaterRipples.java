package naturality.client.fluid;

import java.util.*;
import naturality.config.NaturalityConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Small client-only impact rings; rain uses loaded sky-exposed columns, drips use actual movement. */
public final class WaterRipples {
    private static final int LIFETIME=8, LIMIT=512, RANGE=24;
    private record Ripple(double x,double z,double y,BlockPos origin,boolean cauldron,boolean rain,int born) {}
    private record Appearance(int tint,int light) {}
    private static final List<Ripple> RIPPLES=new ArrayList<>();
    private static @org.jspecify.annotations.Nullable ClientLevel world;
    private static int tick;
    private static final Random RANDOM=new Random();
    public static int lastPixelCount;
    private WaterRipples() {}
    public static int activeCount(){return RIPPLES.size();}
    public static long activeRainCount(){return RIPPLES.stream().filter(Ripple::rain).count();}
    public static long activeRainAt(BlockPos pos){return RIPPLES.stream().filter(r->r.rain && r.origin.equals(pos)).count();}
    private static void sync(@org.jspecify.annotations.Nullable ClientLevel level) {
        if(world!=level) {RIPPLES.clear();world=level;tick=0;lastPixelCount=0;}
    }
    public static void tick(Minecraft client) {
        sync(client.level);
        var world = WaterRipples.world;
        if(client.isPaused()) return;
        if(world==null || !NaturalityConfig.get().liquids.water) {RIPPLES.clear();return;}
        tick++;
        var eye=client.gameRenderer.mainCamera().position();
        RIPPLES.removeIf(r->(r.rain ? !NaturalityConfig.get().liquids.rainRipples : !NaturalityConfig.get().liquids.dripRipples) || tick-r.born>=LIFETIME || eye.distanceToSqr(new Vec3(r.x,r.y,r.z))>32*32);
        float rain=world.getRainLevel(1);
        if(!NaturalityConfig.get().liquids.rainRipples || rain<=0) return;
        var random=RANDOM;
        for(int i=0;i<96;i++) {
            if(random.nextFloat()>rain*0.3F) continue;
            int x=(int)Math.floor(eye.x)+random.nextInt(RANGE*2+1)-RANGE;
            int z=(int)Math.floor(eye.z)+random.nextInt(RANGE*2+1)-RANGE;
            if(!naturality.util.LoadedChunks.has(world, new BlockPos(x,(int)eye.y,z))) continue;
            int y=world.getHeight(Heightmap.Types.MOTION_BLOCKING,x,z)-1;
            var pos=new BlockPos(x,y,z);
            if(Math.abs(y-eye.y)>RANGE || !world.isRainingAt(pos.above())) continue;
            var surface=WaterSurface.at(world,pos);
            if(surface==null) continue;
            double u=random.nextDouble(),v=random.nextDouble();
            if(surface.contains(u,v)) spawn(world,surface,x+u,z+v,true);
        }
    }
    private static void spawn(ClientLevel level,WaterSurface surface,double x,double z,boolean rain) {
        sync(level);
        if(RIPPLES.size()>=LIMIT) return;
        // Centers and output cells share the water's world-space grid, including negative coordinates.
        x=(Math.floor(x*16)+0.5)/16;z=(Math.floor(z*16)+0.5)/16;
        RIPPLES.add(new Ripple(x,z,surface.height(x-surface.pos().getX(),z-surface.pos().getZ()),
            surface.pos(),surface.cauldron(),rain,tick));
    }
    /** Add a ring when a falling leaf settles onto a water surface. */
    public static boolean leafImpact(@org.jspecify.annotations.Nullable ClientLevel level,double x,double y,double z) {
        if(level==null || !NaturalityConfig.get().liquids.water || !NaturalityConfig.get().liquids.dripRipples) return false;
        var surface=WaterSurface.at(level,BlockPos.containing(x,y,z));
        if(surface==null || !surface.contains(x-surface.pos().getX(),z-surface.pos().getZ())) return false;
        spawn(level,surface,x,z,false);
        return true;
    }
    /** Swept downward impact, called after vanilla particle collision movement. */
    public static boolean impact(ClientLevel level,double x0,double y0,double z0,double x,double y,double z) {
        return impact(level,x0,y0,z0,x,y,z,true);
    }
    public static boolean impact(ClientLevel level,double x0,double y0,double z0,double x,double y,double z,boolean emitRing) {
        if(y>=y0 || (emitRing && (!NaturalityConfig.get().liquids.water || !NaturalityConfig.get().liquids.dripRipples))) return false;
        // Subdivide the short segment so a fast droplet cannot skip a fluid tile.
        int steps=Math.min(64,Math.max(1,(int)Math.ceil(Math.max(y0-y,Math.max(Math.abs(x-x0),Math.abs(z-z0)))*16)));
        for(int i=0;i<=steps;i++) {
            double t=i/(double)steps,px=x0+(x-x0)*t,py=y0+(y-y0)*t,pz=z0+(z-z0)*t;
            var surface=WaterSurface.at(level,BlockPos.containing(px,py,pz));
            if(surface==null || !surface.contains(px-surface.pos().getX(),pz-surface.pos().getZ())) continue;
            double h=surface.height(px-surface.pos().getX(),pz-surface.pos().getZ());
            if(y0>=h && y<=h && py<=h+1.0/16) {
                if(emitRing)spawn(level,surface,px,pz,false);
                return true;
            }
        }
        return false;
    }
    /** Pixel-space ring with faint diagonal shoulder pixels rather than a blurred edge. */
    public static float coverage(double x,double z,double radius) {
        int px=(int)Math.round(Math.abs(x)),pz=(int)Math.round(Math.abs(z));
        int r=Math.clamp((int)Math.round(radius),1,4);
        boolean core=circlePixel(px,pz,r);
        // Drop the elbow pixel where two line pixels already touch diagonally.
        // Those corners belong to the faint shoulder, not the solid outline.
        boolean elbow=px>0 && pz>0 && circlePixel(px-1,pz,r) && circlePixel(px,pz-1,r);
        if(core && !elbow) return 0.65F;
        double error=Math.abs(Math.hypot(x,z)-r);
        double diagonal=Math.min(Math.abs(x),Math.abs(z))/Math.max(0.001,Math.max(Math.abs(x),Math.abs(z)));
        return error<=1.25 && diagonal>=0.35 ? 0.08F : 0;
    }
    private static boolean circlePixel(int px,int pz,int radius) {
        int x=radius,z=0,error=1-radius;
        while(x>=z) {
            if(px==x && pz==z || px==z && pz==x) return true;
            z++;
            if(error<0) error+=2*z+1;
            else {x--;error+=2*(z-x)+1;}
        }
        return false;
    }
    static void addTo(WaterIntersection output,ClientLevel level,Vec3 eye,Frustum frustum,float partial) {
        sync(level);
        lastPixelCount=0;
        var surfaces=new HashMap<BlockPos,WaterSurface>();
        var appearances=new HashMap<BlockPos,Appearance>();
        for(var r:RIPPLES) {
            float age=tick-r.born+partial;
            if(age>=LIFETIME || !frustum.isVisible(new AABB(r.x-0.5,r.y-0.1,r.z-0.5,r.x+0.5,r.y+0.1,r.z+0.5))) continue;
            var origin=WaterSurface.at(level,r.origin);
            if(origin==null || origin.cauldron()!=r.cauldron
                || Math.abs(origin.height(r.x-r.origin.getX(),r.z-r.origin.getZ())-r.y)>0.02) continue;
            double radius=1+3*Math.min(1,age/6);
            float fade=Math.min(1,age)*Math.min(1,(LIFETIME-age)/3);
            int cx=(int)Math.floor(r.x*16),cz=(int)Math.floor(r.z*16);
            for(int dx=-6;dx<=6;dx++) for(int dz=-6;dz<=6;dz++) {
                float alpha=coverage(dx,dz,radius)*fade;
                if(alpha<=0) continue;
                int px=cx+dx,pz=cz+dz;
                var pos=new BlockPos(Math.floorDiv(px,16),r.origin.getY(),Math.floorDiv(pz,16));
                if(r.cauldron && !pos.equals(r.origin)) continue;
                var s=surfaces.computeIfAbsent(pos,p->WaterSurface.at(level,p));
                if(s==null || s.cauldron()!=r.cauldron) continue;
                int u=Math.floorMod(px,16),v=Math.floorMod(pz,16);
                double localX=(u+0.5)/16,localZ=(v+0.5)/16;
                if(!s.contains(localX,localZ) || Math.abs(s.height(localX,localZ)-r.y)>0.5) continue;
                var appearance=appearances.computeIfAbsent(pos,p->new Appearance(BiomeColors.getAverageWaterColor(level,p),
                    LightCoordsUtil.max(LightCoordsUtil.getLightCoords(level,p),LightCoordsUtil.getLightCoords(level,p.above()))));
                output.ripplePixel(s,u,v,eye,alpha,appearance.tint,appearance.light);
                lastPixelCount++;
            }
        }
    }
}


