package naturality.client.fluid;

import java.util.*;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/** Temporary pixel-striped artwork on a rectangular animated splash column. */
public final class LavaImpactColumn {
    private record Splash(double x,double y,double z,double width,double depth,double height,long born,int tint,int light) {}
    private static final List<Splash> SPLASHES=new ArrayList<>();
    private static @org.jspecify.annotations.Nullable ClientLevel world;
    public static final double RISE_TICKS=3, FALL_TICKS=40, TOTAL_TICKS=RISE_TICKS+FALL_TICKS, RING_TICKS=20;
    private static double fallProgress(double age){return Math.clamp((age-RISE_TICKS)/FALL_TICKS,0,1);}
    public static double heightFor(double fall){return WaterImpactColumn.heightFor(fall)*.5;}
    public static float envelope(double age) {
        if(age<0 || age>=TOTAL_TICKS)return 0;
        if(age==RISE_TICKS)return 1;
        if(age<RISE_TICKS)return (float)(.08+.92*(1-Math.pow(2,-10*age/RISE_TICKS)));
        return (float)(1-Math.pow(2,10*(fallProgress(age)-1)));
    }
    public static int spreadPixels(double age) {
        double t=fallProgress(age);
        return (int)Math.floor(12*t);
    }
    public static int ringSpreadPixels(double age) {
        return age<=TOTAL_TICKS?spreadPixels(age):12+(int)Math.floor(Math.min(RING_TICKS,age-TOTAL_TICKS)*.6);
    }
    public static float ringFade(double age){return (float)Math.clamp(1-(age-TOTAL_TICKS)/RING_TICKS,0,1);}
    public static int heightPixels(double height,double age,double variation) {
        return Math.max(0,(int)Math.floor(height*envelope(age)*variation*16));
    }

    public static void clear(){SPLASHES.clear();world=null;}
    public static void spawn(ClientLevel level,Entity entity,double fall) {
        if (!naturality.config.NaturalityConfig.get().liquids.lavaImpactColumns) { clear(); return; }
        if(world!=level){clear();world=level;}
        var box=entity.getBoundingBox();
        double x=entity.getX(),z=entity.getZ(),y=entity.getY();
        var pos=BlockPos.containing(x,y,z);
        for(int offset=1;offset>=-1;offset--) {
            var surface=LavaSurface.at(level,pos.above(offset));
            if(surface!=null){y=surface.height(x-Math.floor(x),z-Math.floor(z));break;}
        }
        double height=heightFor(fall),width=Math.max(.35,box.getXsize()*1.15),depth=Math.max(.35,box.getZsize()*1.15);
        if(SPLASHES.size()>=64)SPLASHES.removeFirst();
        SPLASHES.add(new Splash(x,y,z,width,depth,height,level.getGameTime(),LavaEffects.brightColor(),LightCoordsUtil.getLightCoords(level,pos.above())));
        var random=java.util.concurrent.ThreadLocalRandom.current();
        for(int i=0;i<Math.min(48,12+(int)(width*depth*8));i++) {
            var particle=net.minecraft.client.Minecraft.getInstance().particleEngine.createParticle(naturality.NaturalityParticles.LAVA_SPLASH,x+(random.nextDouble()-.5)*width,y+.08,z+(random.nextDouble()-.5)*depth,0,0,0);
            if(particle instanceof naturality.client.particle.ImpactDroplet drop)drop.naturality$landBeforeExpiring();
            if(particle!=null)particle.setParticleSpeed((random.nextDouble()-.5)*.14,.22+Math.sqrt(height)*.18+random.nextDouble()*.08,(random.nextDouble()-.5)*.14);
        }
    }
    public static void render(LavaIntersection output,ClientLevel level,Vec3 eye,float partial) {
        if (!naturality.config.NaturalityConfig.get().liquids.lavaImpactColumns) { clear(); return; }
        if(world!=level){clear();return;}
        SPLASHES.removeIf(s->level.getGameTime()-s.born>=TOTAL_TICKS+RING_TICKS);
        for(var s:SPLASHES) {
            double age=level.getGameTime()-s.born+partial;
            float alpha=(float)Math.clamp((TOTAL_TICKS-age)/6*.85,0,.85);
            int spread=ringSpreadPixels(age);
            int left=(int)Math.floor((s.x-s.width/2)*16)-spread,right=(int)Math.ceil((s.x+s.width/2)*16)+spread;
            int north=(int)Math.floor((s.z-s.depth/2)*16)-spread,south=(int)Math.ceil((s.z+s.depth/2)*16)+spread;
            double width=(right-left)/16.0,depth=(south-north)/16.0;
            // Same pixel grid and soft outer rows as the entity rim, with the rain-ring palette.
            for(int px=left-2;px<right+2;px++)for(int pz=north-2;pz<south+2;pz++) {
                int edge=Math.min(Math.min(px-left,right-1-px),Math.min(pz-north,south-1-pz));
                if(edge>0)continue;
                float opacity=(edge==0?.72F:edge==-1?.34F:.10F)*ringFade(age);
                var pos=BlockPos.containing((px+.5)/16,s.y,(pz+.5)/16);
                LavaSurface surface=null;
                for(int dy=1;dy>=-1;dy--) {
                    var candidate=LavaSurface.at(level,pos.above(dy));
                    if(candidate!=null && Math.abs(candidate.height(.5,.5)-s.y)<.5){surface=candidate;break;}
                }
                if(surface==null)continue;
                int lx=Math.floorMod(px,16),lz=Math.floorMod(pz,16);
                if(!surface.contains((lx+.5)/16,(lz+.5)/16))continue;
                output.ripplePixel(surface,lx,lz,eye,opacity,LavaEffects.brightColor(),s.light);
            }
            if(age>=TOTAL_TICKS)continue;
            for(int side=0;side<4;side++) {
                int columns=Math.max(4,(int)Math.ceil((side%2==0?width:depth)*16));
                for(int col=0;col<columns;col++) {
                    // Re-evaluate a centered virtual pattern at each new raster cell;
                    // resizing never anchors the design to a moving left edge.
                    int textureX=(int)Math.floor(((col+.5)/columns-.5)*32)+32;
                    float crest=LavaEffects.heat(textureX,side*13,age);
                    int rows=heightPixels(s.height,age,.72+crest*.28);
                    double a=col/(double)columns,b=(col+1)/(double)columns;
                    for(int row=0;row<rows;row++) {
                        double bottom=row/16.0,upper=(row+1)/16.0,x0=left/16.0,z0=north/16.0;
                        double vertical=(row+.5)/rows;
                        int textureY=(int)Math.floor((vertical-.5)*32)+32+side*13;
                        float heat=LavaEffects.heat(textureX,textureY,age);

                        // Dark molten lobes at the root and hot yellow-orange crests.
                        double foam=Math.max(0,Math.min(1,(vertical-(.50+heat*.18))/.27));
                        float blend=(float)(foam*foam*(3-2*foam));
                        Vec3 p,q,r,t;
                        if(side%2==0) {
                            double z=z0+(side==2?depth:0);
                            p=new Vec3(x0+a*width,s.y+bottom,z);q=new Vec3(x0+b*width,s.y+bottom,z);
                            r=new Vec3(x0+b*width,s.y+upper,z);t=new Vec3(x0+a*width,s.y+upper,z);
                        } else {
                            double x=x0+(side==1?width:0);
                            p=new Vec3(x,s.y+bottom,z0+a*depth);q=new Vec3(x,s.y+bottom,z0+b*depth);
                            r=new Vec3(x,s.y+upper,z0+b*depth);t=new Vec3(x,s.y+upper,z0+a*depth);
                        }
                        int color=LavaEffects.color(heat,blend);
                        int tint=ARGB.colorFromFloat(alpha,ARGB.red(color)/255F,ARGB.green(color)/255F,ARGB.blue(color)/255F);
                        output.splashQuad(p.subtract(eye),q.subtract(eye),r.subtract(eye),t.subtract(eye),tint,s.light);
                    }
                }
            }
        }
    }
}












