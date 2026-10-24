package naturality.client.fluids.lava;

import naturality.client.api.geometry.PlanarGeometry;
import naturality.client.api.geometry.ModelCapture;
import naturality.client.fluids.lava.LavaEffects.LavaShaders;
import naturality.client.fluids.lava.LavaEffects.LavaSurface;
import naturality.client.fluids.shared.FluidGeometry.WaterIntersectionGeometry;
import java.util.*;
import com.mojang.blaze3d.vertex.VertexConsumer;
import naturality.config.live.NaturalityConfig;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.state.level.*;
import net.minecraft.core.BlockPos;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import naturality.client.fluids.water.WaterIntersection.WaterImpactColumn;
import naturality.client.fluids.shared.FluidGeometry.LiquidSurfaceGeometry;
import net.minecraft.world.entity.Entity;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.Projectile;
import naturality.client.fluids.shared.FallingFluidTail.FallImpactDroplets;
import naturality.client.fluids.shared.FallingFluidTail.FallParticleDensity;
import naturality.client.fluids.shared.FallingFluidTail.FluidSectionScan;
import naturality.bootstrap.ContentRegistration.NaturalityParticles;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ParticleStatus;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.material.FlowingFluid;
import naturality.client.fluids.shared.FallingFluidTail.LiquidDropletContact;
import naturality.client.fluids.shared.FallingFluidTail.ImpactDroplet;
import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import naturality.client.fluids.shared.FallingFluidTail.BorderSplashParticle;
import naturality.client.fluids.shared.FallingFluidTail.FallParticleBudget;
import net.fabricmc.fabric.api.client.particle.v1.FabricSpriteSet;
import net.minecraft.util.RandomSource;

/** Model cross-section foam, rebuilt from the current pose and local fluid geometry every frame. */
@org.jspecify.annotations.NullMarked
public final class LavaIntersection extends QuadParticleRenderState {
    private static final SingleQuadParticle.Layer LAYER = LavaShaders.Intersection.LAYER;
    private static final SingleQuadParticle.Layer SPLASH_LAYER = LavaShaders.Intersection.SPLASH_LAYER;

    private record Wake(BlockPos pos,int x,int z,float alpha,long tick) {}
    private static final Map<String,Wake> WAKES=new HashMap<>();
    private static @org.jspecify.annotations.Nullable ClientLevel wakeWorld;
    private record Pixel(Vec3 a,Vec3 b,Vec3 c,Vec3 d,int color,int light) {}
    private static final class Tile {
        final LavaSurface surface;
        final List<PlanarGeometry.Rect> first=new ArrayList<>(),second=new ArrayList<>();
        Tile(LavaSurface surface) {this.surface=surface;}
    }
    /** Extraction-owned packed vertices: no per-pixel records or four temporary vectors. */
    private static final class SurfacePixels {
        private float[] positions=new float[0];
        private int[] attributes=new int[0];
        private int count;
        int size(){return count;}
        boolean isEmpty(){return count==0;}
        void clear(){count=0;}
        void add(LavaSurface surface,int x,int z,Vec3 eye,int color,int light) {
            if(count*2==attributes.length) {
                int capacity=Math.max(64,attributes.length);
                attributes=Arrays.copyOf(attributes,capacity*2);
                positions=Arrays.copyOf(positions,capacity*12);
            }
            double left=x/16.0,right=(x+1)/16.0,bottom=z/16.0,top=(z+1)/16.0;
            put(count*12,surface,left,bottom,eye);
            put(count*12+3,surface,left,top,eye);
            put(count*12+6,surface,right,top,eye);
            put(count*12+9,surface,right,bottom,eye);
            attributes[count*2]=color; attributes[count*2+1]=light; count++;
        }
        private void put(int index,LavaSurface surface,double x,double z,Vec3 eye) {
            positions[index]=(float)(surface.pos().getX()-eye.x+x);
            positions[index+1]=(float)(surface.height(x,z)-eye.y+1.0/1024);
            positions[index+2]=(float)(surface.pos().getZ()-eye.z+z);
        }
        void build(VertexConsumer buffer) {
            for(int pixel=0;pixel<count;pixel++) for(int corner=0;corner<4;corner++) {
                int index=pixel*12+corner*3;
                buffer.addVertex(positions[index],positions[index+1],positions[index+2]).setUv(0,0)
                    .setColor(attributes[pixel*2]).setLight(attributes[pixel*2+1]);
            }
        }
    }
    private final SurfacePixels pixels=new SurfacePixels();
    private final List<Pixel> splashPixels=new ArrayList<>();
    public static void initialize() {}
    public static int lastPixelCount;

    public static void extract(ParticlesRenderState output,Frustum frustum,Camera camera,float partial) {
        lastPixelCount=0;
        LavaRipples.lastPixelCount=0;
        var client=Minecraft.getInstance();
        var level=client.level;
        if(level!=wakeWorld){WAKES.clear();wakeWorld=level;}
        if(level==null || !NaturalityConfig.get().liquids.lava){WAKES.clear();return;}
        var eye=camera.position();
        var tiles=new HashMap<BlockPos,Tile>();
        int captured=0;
        var entities=new ArrayList<net.minecraft.world.entity.Entity>();
        for(var entity:level.entitiesForRendering())
            if(NaturalityConfig.get().liquids.lavaRim && !entity.isSpectator() && !entity.isInvisible() && entity.distanceToSqr(eye)<32*32) entities.add(entity);
        entities.sort(Comparator.comparingDouble(e->e.distanceToSqr(eye)));
        for(var entity:entities) {
            if(entity==camera.entity() && client.options.getCameraType().isFirstPerson()) continue;
            var box=entity.getBoundingBox().move(entity.getPosition(partial).subtract(entity.position())).inflate(1);
            if(!frustum.isVisible(box) || box.getXsize()>16 || box.getYsize()>16 || box.getZsize()>16) continue;
            var candidates=new ArrayList<BlockPos>();
            for(var p:BlockPos.betweenClosed((int)Math.floor(box.minX),(int)Math.floor(box.minY),(int)Math.floor(box.minZ),
                    (int)Math.floor(box.maxX),(int)Math.floor(box.maxY),(int)Math.floor(box.maxZ))) {
                if(LavaSurface.at(level,p)==null) continue;
                candidates.add(p.immutable());
            }
            if(candidates.isEmpty()) continue;
            if(captured++>=64) break;
            var origin=Vec3.atLowerCornerOf(BlockPos.containing(entity.getPosition(partial)));
            var mesh=ModelCapture.mesh(entity,origin,camera,partial);
            if(mesh.isEmpty()) continue; // Never invent a hitbox outline for a non-model renderer.
            for(var pos:candidates) {
                var surface = LavaSurface.at(level,pos);
                if (surface == null) continue;
                var tile=tiles.computeIfAbsent(pos,_->new Tile(surface));
                var s=tile.surface;
                double left=pos.getX()-origin.x, bottom=pos.getZ()-origin.z;
                double dx=s.se()-s.sw(),dz=s.sw()-s.nw();
                double height=s.height(0,0)-origin.y-dx*left-dz*bottom;
                tile.first.addAll(worldSections(mesh,height,dx,dz,left,bottom,origin));
                dx=s.ne()-s.nw(); dz=s.se()-s.ne();
                height=s.height(0,0)-origin.y-dx*left-dz*bottom;
                tile.second.addAll(worldSections(mesh,height,dx,dz,left,bottom,origin));
            }
        }
        var state=new LavaIntersection();
        for(var tile:tiles.values()) state.add(level,tile,eye);
        state.addWake(level,eye);
        LavaImpactColumn.render(state,level,eye,partial);
        lastPixelCount=state.pixels.size();
        LavaRipples.addTo(state,level,eye,frustum,partial);
        if(!state.isEmpty()) output.add(state);
    }
    private static List<PlanarGeometry.Rect> worldSections(List<Vec3[]> mesh,double h,double dx,double dz,
                                                               double x,double z,Vec3 origin) {
        var local=WaterIntersectionGeometry.section(mesh,h,dx,dz,x-0.25,z-0.25,x+1.25,z+1.25);
        var result=new ArrayList<PlanarGeometry.Rect>();
        for(var r:local) result.add(new PlanarGeometry.Rect(r.left()+origin.x,r.bottom()+origin.z,r.right()+origin.x,r.top()+origin.z));
        return result;
    }
    private void add(ClientLevel level,Tile tile,Vec3 eye) {
        var s=tile.surface; var p=s.pos();
        int tint=LavaEffects.brightColor();
        float r=ARGB.red(tint)/255F,g=ARGB.green(tint)/255F,b=ARGB.blue(tint)/255F;
        // procedural_fluids.glsl peaks at (50 + 64) / 114 = 1 before biome tint.
        // Match that brightest water color, without extra brightness or added white.
        int light=LightCoordsUtil.max(LightCoordsUtil.getLightCoords(level,p),LightCoordsUtil.getLightCoords(level,p.above()));
        for(int x=0;x<16;x++) for(int z=0;z<16;z++) {
            double u=(x+0.5)/16,v=(z+0.5)/16;
            if(!s.contains(u,v)) continue;
            float alpha=WaterIntersectionGeometry.alpha(z>=x?tile.first:tile.second,p.getX()+u,p.getZ()+v);
            if(alpha==0) continue;
            long tick=level.getGameTime();
            int hash=Objects.hash(p.getX()*16+x,p.getZ()*16+z,tick/4);
            alpha*=0.86F+Math.floorMod(hash,15)/100F;
            if(WAKES.size()<12000)WAKES.put(p+"/"+x+"/"+z,new Wake(p,x,z,alpha,tick));
            int color=ARGB.colorFromFloat(alpha,r,g,b);
            pixels.add(s,x,z,eye,color,light);
        }
    }
    void splashQuad(Vec3 a,Vec3 b,Vec3 c,Vec3 d,int tint,int light){splashPixels.add(new Pixel(a,b,c,d,tint,light));}
    @Override public Set<SingleQuadParticle.Layer> layers() {
        var layers=new HashSet<SingleQuadParticle.Layer>();
        if(!pixels.isEmpty())layers.add(LAYER);
        if(!splashPixels.isEmpty())layers.add(SPLASH_LAYER);
        return layers;
    }
    @Override public boolean isEmpty(){return pixels.isEmpty()&&splashPixels.isEmpty();}
    @Override public void clear(){pixels.clear();splashPixels.clear();}
    @Override public void buildLayer(SingleQuadParticle.Layer layer,VertexConsumer buffer) {
        if(layer==LAYER){pixels.build(buffer);return;}
        var draws=layer==SPLASH_LAYER?splashPixels:List.<Pixel>of();
        for(var pixel:draws) {
            vertex(buffer,pixel.a,pixel.color,pixel.light);
            vertex(buffer,pixel.b,pixel.color,pixel.light);
            vertex(buffer,pixel.c,pixel.color,pixel.light);
            vertex(buffer,pixel.d,pixel.color,pixel.light);
        }
    }
    private static void vertex(VertexConsumer buffer,Vec3 p,int color,int light) {
        buffer.addVertex((float)p.x,(float)p.y,(float)p.z).setUv(0,0).setColor(color).setLight(light);
    }
    private void addWake(ClientLevel level,Vec3 eye) {
        long tick=level.getGameTime();
        if(!NaturalityConfig.get().liquids.lavaRim || !NaturalityConfig.get().liquids.lavaWake){WAKES.clear();return;}
        WAKES.values().removeIf(w->tick-w.tick>6);
        for(var w:WAKES.values()) {
            long age=tick-w.tick;
            if(age<=0 || w.pos.distToCenterSqr(eye)>32*32)continue;
            var surface=LavaSurface.at(level,w.pos);
            if(surface==null)continue;
            float alpha=w.alpha*.22F*(1-age/7F);
            int tint=LavaEffects.brightColor();
            int color=ARGB.colorFromFloat(alpha,ARGB.red(tint)/255F,ARGB.green(tint)/255F,ARGB.blue(tint)/255F);
            int light=LightCoordsUtil.getLightCoords(level,w.pos.above());
            pixels.add(surface,w.x,w.z,eye,color,light);
        }
    }
    void ripplePixel(LavaSurface surface,int x,int z,Vec3 eye,float alpha,int tint,int light) {
        float r=ARGB.red(tint)/255F;
        float g=ARGB.green(tint)/255F;
        float b=ARGB.blue(tint)/255F;
        pixels.add(surface,x,z,eye,ARGB.colorFromFloat(alpha,r,g,b),light);
    }
    @Override public void submit(SubmitNodeCollector collector,CameraRenderState camera){if(!isEmpty())collector.submitQuadParticleGroup(this);}

    /** Client-only lava impact rings and rain-quench smoke on sky-exposed lava. */
    public static final class LavaRipples {
        private static final int LIFETIME=16, LIMIT=512, RANGE=24;
        private record Ripple(double x,double z,double y,BlockPos origin,boolean cauldron,int born) {}
        private record Appearance(int tint,int light) {}
        private static final List<Ripple> RIPPLES=new ArrayList<>();
        private static @org.jspecify.annotations.Nullable ClientLevel world;
        private static int tick;
        private static final Random RANDOM=new Random();
        public static int lastPixelCount,rainSmokeSpawnCount;
        private LavaRipples() {}
        public static int activeCount(){return RIPPLES.size();}
        private static void sync(@org.jspecify.annotations.Nullable ClientLevel level) {
            if(world!=level) {RIPPLES.clear();world=level;tick=0;lastPixelCount=rainSmokeSpawnCount=0;}
        }
        public static void tick(Minecraft client) {
            sync(client.level);
            var world = LavaRipples.world;
            if(client.isPaused()) return;
            if(world==null || !NaturalityConfig.get().liquids.lava) {RIPPLES.clear();return;}
            tick++;
            var eye=client.gameRenderer.mainCamera().position();
            RIPPLES.removeIf(r->!NaturalityConfig.get().liquids.lavaDripRipples || tick-r.born>=LIFETIME || eye.distanceToSqr(new Vec3(r.x,r.y,r.z))>32*32);
            float rain=world.getRainLevel(1);
            if(!NaturalityConfig.get().liquids.lavaRainRipples || rain<=0) return;
            var random=RANDOM;
            for(int i=0;i<96;i++) {
                if(random.nextFloat()>rain*0.12F) continue;
                int x=(int)Math.floor(eye.x)+random.nextInt(RANGE*2+1)-RANGE;
                int z=(int)Math.floor(eye.z)+random.nextInt(RANGE*2+1)-RANGE;
                if(!naturality.api.worlds.LoadedChunks.has(world, new BlockPos(x,(int)eye.y,z))) continue;
                int y=world.getHeight(Heightmap.Types.MOTION_BLOCKING,x,z)-1;
                var pos=new BlockPos(x,y,z);
                if(Math.abs(y-eye.y)>RANGE || !world.isRainingAt(pos.above())) continue;
                var surface=LavaSurface.at(world,pos);
                if(surface==null) continue;
                double u=random.nextDouble(),v=random.nextDouble();
                if(surface.contains(u,v)) {
                    double height=surface.height(u,v);
                    var smoke=client.particleEngine.createParticle(ParticleTypes.SMOKE,x+u,height+0.04,z+v,0,0.015,0);
                    if(smoke!=null) {smoke.scale(0.3F);rainSmokeSpawnCount++;}
                }
            }
        }
        private static void spawn(ClientLevel level,LavaSurface surface,double x,double z) {
            sync(level);
            if(RIPPLES.size()>=LIMIT) return;
            // Centers and output cells share the water's world-space grid, including negative coordinates.
            x=(Math.floor(x*16)+0.5)/16;z=(Math.floor(z*16)+0.5)/16;
            RIPPLES.add(new Ripple(x,z,surface.height(x-surface.pos().getX(),z-surface.pos().getZ()),
                surface.pos(),surface.cauldron(),tick));
        }
        /** Swept downward impact, called after vanilla particle collision movement. */
        public static boolean impact(ClientLevel level,double x0,double y0,double z0,double x,double y,double z) {
            return impact(level,x0,y0,z0,x,y,z,true);
        }
        public static boolean impact(ClientLevel level,double x0,double y0,double z0,double x,double y,double z,boolean emitRing) {
            if(y>=y0 || (emitRing && (!NaturalityConfig.get().liquids.lava || !NaturalityConfig.get().liquids.lavaDripRipples))) return false;
            // Subdivide the short segment so a fast droplet cannot skip a fluid tile.
            int steps=Math.min(64,Math.max(1,(int)Math.ceil(Math.max(y0-y,Math.max(Math.abs(x-x0),Math.abs(z-z0)))*16)));
            for(int i=0;i<=steps;i++) {
                double t=i/(double)steps,px=x0+(x-x0)*t,py=y0+(y-y0)*t,pz=z0+(z-z0)*t;
                var surface=LavaSurface.at(level,BlockPos.containing(px,py,pz));
                if(surface==null || !surface.contains(px-surface.pos().getX(),pz-surface.pos().getZ())) continue;
                double h=surface.height(px-surface.pos().getX(),pz-surface.pos().getZ());
                if(y0>=h && y<=h && py<=h+1.0/16) {
                    if(emitRing)spawn(level,surface,px,pz);
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
        // Radius is rounded by coverage(): these four integer lattices are exact,
        // including the faint shoulders, and do not depend on position or time.
        private static final float[][] RING_COVERAGE=ringCoverage();
        private static float[][] ringCoverage() {
            float[][] result=new float[4][169];
            for(int radius=1;radius<=4;radius++) for(int x=-6;x<=6;x++) for(int z=-6;z<=6;z++)
                result[radius-1][(x+6)*13+z+6]=coverage(x,z,radius);
            return result;
        }
        static void addTo(LavaIntersection output,ClientLevel level,Vec3 eye,Frustum frustum,float partial) {
            sync(level);
            lastPixelCount=0;
            var surfaces=new HashMap<BlockPos,LavaSurface>();
            var appearances=new HashMap<BlockPos,Appearance>();
            for(var r:RIPPLES) {
                float age=tick-r.born+partial;
                if(age>=LIFETIME || !frustum.isVisible(new AABB(r.x-0.5,r.y-0.1,r.z-0.5,r.x+0.5,r.y+0.1,r.z+0.5))) continue;
                var origin=LavaSurface.at(level,r.origin);
                if(origin==null || origin.cauldron()!=r.cauldron
                    || Math.abs(origin.height(r.x-r.origin.getX(),r.z-r.origin.getZ())-r.y)>0.02) continue;
                double radius=1+3*Math.min(1,age/12);
                var coverage=RING_COVERAGE[Math.clamp((int)Math.round(radius),1,4)-1];
                float fade=Math.min(1,age/2)*Math.min(1,(LIFETIME-age)/6);
                int cx=(int)Math.floor(r.x*16),cz=(int)Math.floor(r.z*16);
                for(int dx=-6;dx<=6;dx++) for(int dz=-6;dz<=6;dz++) {
                    float alpha=coverage[(dx+6)*13+dz+6]*fade;
                    if(alpha<=0) continue;
                    int px=cx+dx,pz=cz+dz;
                    var pos=new BlockPos(Math.floorDiv(px,16),r.origin.getY(),Math.floorDiv(pz,16));
                    if(r.cauldron && !pos.equals(r.origin)) continue;
                    var s=surfaces.computeIfAbsent(pos,p->LavaSurface.at(level,p));
                    if(s==null || s.cauldron()!=r.cauldron) continue;
                    int u=Math.floorMod(px,16),v=Math.floorMod(pz,16);
                    double localX=(u+0.5)/16,localZ=(v+0.5)/16;
                    if(!s.contains(localX,localZ) || Math.abs(s.height(localX,localZ)-r.y)>0.5) continue;
                    var appearance=appearances.computeIfAbsent(pos,p->new Appearance(LavaEffects.brightColor(),
                        LightCoordsUtil.max(LightCoordsUtil.getLightCoords(level,p),LightCoordsUtil.getLightCoords(level,p.above()))));
                    output.ripplePixel(s,u,v,eye,alpha,appearance.tint,appearance.light);
                    lastPixelCount++;
                }
            }
        }
    }

    /** Temporary pixel-striped artwork on a rectangular animated splash column. */
    public static final class LavaImpactColumn {
        private record Splash(double x,double y,double z,double width,double depth,double height,double sizeScale,long born,int tint,int light) {}
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
            if (!naturality.config.live.NaturalityConfig.get().liquids.lavaImpactColumns) { clear(); return; }
            if(world!=level){clear();world=level;}
            var box=entity.getBoundingBox();
            double x=entity.getX(),z=entity.getZ(),y=entity.getY();
            var pos=BlockPos.containing(x,y,z);
            for(int offset=1;offset>=-1;offset--) {
                var surface=LavaSurface.at(level,pos.above(offset));
                if(surface!=null){y=surface.height(x-Math.floor(x),z-Math.floor(z));break;}
            }
            boolean small=entity instanceof net.minecraft.world.entity.item.ItemEntity || entity instanceof net.minecraft.world.entity.projectile.Projectile;
            double sizeScale=small?.5:1;
            double height=heightFor(fall)*(small?.25:1),width=Math.max(.35,box.getXsize()*1.15)*sizeScale,depth=Math.max(.35,box.getZsize()*1.15)*sizeScale;
            if(SPLASHES.size()>=64)SPLASHES.removeFirst();
            SPLASHES.add(new Splash(x,y,z,width,depth,height,sizeScale,level.getGameTime(),LavaEffects.brightColor(),LightCoordsUtil.getLightCoords(level,pos.above())));
            var random=java.util.concurrent.ThreadLocalRandom.current();
            for(int i=0;i<Math.min(48,(small?6:12)+(int)(width*depth*8));i++) {
                var particle=net.minecraft.client.Minecraft.getInstance().particleEngine.createParticle(naturality.bootstrap.ContentRegistration.NaturalityParticles.LAVA_SPLASH,x+(random.nextDouble()-.5)*width,y+.08,z+(random.nextDouble()-.5)*depth,0,0,0);
                if(particle instanceof naturality.client.fluids.shared.FallingFluidTail.ImpactDroplet drop)drop.naturality$landBeforeExpiring();
                if(particle!=null) {
                    if(small)particle.scale(.65F);
                    particle.setParticleSpeed((random.nextDouble()-.5)*.14*sizeScale,(.22+Math.sqrt(height)*.18+random.nextDouble()*.08)*sizeScale,(random.nextDouble()-.5)*.14*sizeScale);
                }
            }
        }
        public static void render(LavaIntersection output,ClientLevel level,Vec3 eye,float partial) {
            if (!naturality.config.live.NaturalityConfig.get().liquids.lavaImpactColumns) { clear(); return; }
            if(world!=level){clear();return;}
            SPLASHES.removeIf(s->level.getGameTime()-s.born>=TOTAL_TICKS+RING_TICKS);
            for(var s:SPLASHES) {
                double age=level.getGameTime()-s.born+partial;
                float alpha=(float)Math.clamp((TOTAL_TICKS-age)/6*.85,0,.85);
                int spread=(int)Math.floor(ringSpreadPixels(age)*s.sizeScale);
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
                    for(int dy=1;dy>=-3;dy--) {
                        var candidate=LavaSurface.at(level,pos.above(dy));
                        if(candidate!=null && Math.abs(candidate.height(.5,.5)-s.y)<3){surface=candidate;break;}
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
                        double cx=left/16.0+(side%2==0?(a+b)*.5*width:side==1?width-1.0/32:1.0/32);
                        double cz=north/16.0+(side%2!=0?(a+b)*.5*depth:side==2?depth-1.0/32:1.0/32);
                        var contact=LiquidSurfaceGeometry.near(level,cx,s.y,cz,true,false);
                        if(contact==null || Math.abs(contact.height()-s.y)>3)continue;
                        double baseY=Math.floor(contact.height()*16)/16;
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
                                p=new Vec3(x0+a*width,baseY+bottom,z);q=new Vec3(x0+b*width,baseY+bottom,z);
                                r=new Vec3(x0+b*width,baseY+upper,z);t=new Vec3(x0+a*width,baseY+upper,z);
                            } else {
                                double x=x0+(side==1?width:0);
                                p=new Vec3(x,baseY+bottom,z0+a*depth);q=new Vec3(x,baseY+bottom,z0+b*depth);
                                r=new Vec3(x,baseY+upper,z0+b*depth);t=new Vec3(x,baseY+upper,z0+a*depth);
                            }
                            int color=LavaEffects.color(heat,blend);
                            int tint=ARGB.colorFromFloat(alpha,ARGB.red(color)/255F,ARGB.green(color)/255F,ARGB.blue(color)/255F);
                            boolean reversed=side<2;
                            output.splashQuad(p.subtract(eye),(reversed?t:q).subtract(eye),r.subtract(eye),(reversed?q:t).subtract(eye),tint,
                                naturality.config.live.NaturalityConfig.get().liquids.emissiveLava?LightCoordsUtil.FULL_BRIGHT:s.light);
                        }
                    }
                }
            }
        }
    }

    /** Tracks actual descent because vanilla clears fallDistance before the water-entry effect. */
    public static final class LavaEntrySplash {
        private record Fall(double y,double distance,boolean water) {}
        private static final Map<Integer,Fall> FALLS=new HashMap<>();
        private static @org.jspecify.annotations.Nullable ClientLevel level;
        public static int lastSplashCount;
        public static void tick(Minecraft client) {
            lastSplashCount=0;
            if(level!=client.level) {level=client.level;FALLS.clear();}
            var level = LavaEntrySplash.level;
            if(level==null || !NaturalityConfig.get().liquids.lava) {FALLS.clear();LavaImpactColumn.clear();return;}
            if(client.isPaused())return;
            var seen=new HashSet<Integer>();
            for(var entity:level.entitiesForRendering()) {
                if(!(entity instanceof LivingEntity) && !(entity instanceof net.minecraft.world.entity.vehicle.boat.AbstractBoat)
                    && !(entity instanceof Projectile) && !(entity instanceof ItemEntity) || entity.isSpectator())continue;
                seen.add(entity.getId());
                var old=FALLS.get(entity.getId());
                            var contact=net.minecraft.core.BlockPos.containing(entity.getX(),entity.getY()+.05,entity.getZ());
                var fluid=level.getFluidState(contact);
                boolean water=fluid.is(net.minecraft.tags.FluidTags.LAVA) && entity.getY()<contact.getY()+fluid.getHeight(level,contact);
                double descent=old==null?0:old.y-entity.getY();
                // Ignore teleports; normal falling motion must account for the displacement.
                boolean continuous=descent>=0 && descent<=Math.max(3,Math.abs(entity.getDeltaMovement().y)*2+1);
                double distance=old!=null && !old.water && continuous?old.distance+descent:0;
                if(water && old!=null && !old.water && distance>5 && !entity.isSilent() && !entity.isPassenger()) {

                    lastSplashCount++;
                    LavaImpactColumn.spawn(level,entity,distance);
                }
                if(water || (entity.onGround() && Math.abs(descent)<.001) || entity.isPassenger() || (entity instanceof LivingEntity living && living.onClimbable()) || !continuous)distance=0;
                FALLS.put(entity.getId(),new Fall(entity.getY(),distance,water));
            }
            FALLS.keySet().retainAll(seen);
        }
    }

    /** Bounded search for descending water surfaces meeting flat, exposed resting water. */
    public static final class LavafallSplash {
        private static final int RANGE=FallParticleDensity.RANGE, SEARCH_RANGE=RANGE+8, WIDTH=SEARCH_RANGE*2+1;
        private static final int[] SCAN_ORDER=FallParticleDensity.scanOrder(SEARCH_RANGE);
        public record Border(BlockPos pool,Direction side,int drop) {
            public Border(BlockPos pool,Direction side){this(pool,side,0);}
            public BlockPos receiving(){return pool.relative(side).below(drop);}
        }
        private static final Map<Long,List<Border>> COLUMNS=new HashMap<>();
        private static final Map<Long,List<BlockPos>> FALLS=new HashMap<>();
        private static final Set<Border> ACTIVE=new HashSet<>();
        private static final Random RANDOM=new Random();
        private static @org.jspecify.annotations.Nullable ClientLevel world;
        private static @org.jspecify.annotations.Nullable BlockPos center;
        private static int cursor;
        private static final FluidSectionScan SCAN = new FluidSectionScan();
        public static int lastSmallSpawns,lastBigSpawns;
        private LavafallSplash() {}
        public static boolean impactColumn(ClientLevel level,BlockPos pool) {
            if(!naturality.api.worlds.LoadedChunks.has(level, pool))return false;
            var water=level.getFluidState(pool);
            if(!water.is(FluidTags.LAVA))return false;
            var surface=LavaSurface.at(level,pool);
            if(surface!=null)return !water.isSource() && relief(surface)>1.0/64;
            var above=level.getFluidState(pool.above());
            return water.isSource() && above.is(FluidTags.LAVA) && !above.isSource()
                && above.getValue(FlowingFluid.FALLING);
        }
        private static double relief(LavaSurface s) {
            return Math.max(Math.max(s.nw(),s.ne()),Math.max(s.sw(),s.se()))
                -Math.min(Math.min(s.nw(),s.ne()),Math.min(s.sw(),s.se()));
        }
        private static void refreshColumn(int x,int z) {
            long key=BlockPos.asLong(x,0,z);
            COLUMNS.remove(key);FALLS.remove(key);
            var world = LavafallSplash.world;
            var center = LavafallSplash.center;
            if(world == null || center == null)return;
            var chunk = world.getChunkSource().getChunk(x >> 4, z >> 4, false);
            if(chunk == null)return;
            var borders=new ArrayList<Border>();
            var falls=new ArrayList<BlockPos>();
            var position = new BlockPos.MutableBlockPos();
            int minY = Math.max(world.getMinY(), center.getY()-SEARCH_RANGE);
            int maxY = Math.min(world.getMaxY(), center.getY()+SEARCH_RANGE);
            for(int y=minY;y<=maxY;y++) {
                if ((y == minY || (y & 15) == 0) && !SCAN.contains(chunk, y >> 4, true)) { y |= 15; continue; }
                position.set(x,y,z);
                if (!chunk.getFluidState(position).is(FluidTags.LAVA)) continue;
                var pool=position.immutable();
                if(FallImpactDroplets.falling(world,pool,true))falls.add(pool);
                if(!impactColumn(world,pool))continue;
                for(var side:Direction.Plane.HORIZONTAL)for(int drop=0;drop<=1;drop++) {
                    var border=new Border(pool,side,drop);
                    if(validBorder(world,border))borders.add(border);
                }
            }
            if(!borders.isEmpty())COLUMNS.put(key,borders);
            if(!falls.isEmpty())FALLS.put(key,falls);
        }
        public static boolean validBorder(ClientLevel level,Border border) {
            if(!impactColumn(level,border.pool))return false;
            var neighbor=border.receiving();
            if(!naturality.api.worlds.LoadedChunks.has(level, neighbor))return false;
            var rest=level.getFluidState(neighbor);
            var receiving=LavaSurface.at(level,neighbor);
            // Spray belongs at the foot of the slope, not its upper junction with
            // the falling column. Even source blocks can have a sloping rendered top.
            if(!rest.is(FluidTags.LAVA) || !rest.isSource() || receiving==null)return false;
            var incoming=LavaSurface.at(level,border.pool);
            if(incoming==null)return border.drop==0;
            double dx=border.side.getStepX(),dz=border.side.getStepZ();
            double high=incoming.height(0.5-dx*0.4,0.5-dz*0.4);
            double low=incoming.height(0.5+dx*0.4,0.5+dz*0.4);
            return high-low>1.0/64 && low>=receiving.height(0.5,0.5)-1.0/64;
        }
        public static void tick(Minecraft client) {
            lastSmallSpawns=lastBigSpawns=0;
            if(world!=client.level) {world=client.level;center=null;COLUMNS.clear();FALLS.clear();ACTIVE.clear();FallImpactDroplets.reset(true);cursor=0;}
            var world = LavafallSplash.world;
            if(client.isPaused())return;
            if(world==null || !NaturalityConfig.get().liquids.lava || !NaturalityConfig.get().liquids.lavafallParticles
                || client.options.particles().get()==ParticleStatus.MINIMAL) {COLUMNS.clear();FALLS.clear();ACTIVE.clear();FallImpactDroplets.reset(true);center=null;return;}
            var eye=client.gameRenderer.mainCamera().position();
            var camera=BlockPos.containing(eye);
            var oldCenter = center;
            if(oldCenter==null || Math.abs(camera.getX()-oldCenter.getX())>8 || Math.abs(camera.getY()-oldCenter.getY())>8
                || Math.abs(camera.getZ()-oldCenter.getZ())>8) {center=camera;cursor=0;
                COLUMNS.keySet().removeIf(key->Math.abs(BlockPos.getX(key)-camera.getX())>SEARCH_RANGE
                    || Math.abs(BlockPos.getZ(key)-camera.getZ())>SEARCH_RANGE);
                FALLS.keySet().removeIf(key->Math.abs(BlockPos.getX(key)-camera.getX())>SEARCH_RANGE
                    || Math.abs(BlockPos.getZ(key)-camera.getZ())>SEARCH_RANGE);
            }
            var center = java.util.Objects.requireNonNull(LavafallSplash.center);
            FALLS.keySet().removeIf(key->Math.abs(BlockPos.getX(key)-center.getX())>SEARCH_RANGE || Math.abs(BlockPos.getZ(key)-center.getZ())>SEARCH_RANGE);
            SCAN.reset(); // No block/packet mutations can interleave this client-thread batch.
            // Keep an eight-block search margin for camera movement; refresh without chunk loads.
            for(int i=0;i<256;i++) {
                int column=SCAN_ORDER[cursor];
                cursor=(cursor+1)%SCAN_ORDER.length;
                int x=center.getX()-SEARCH_RANGE+column%WIDTH,z=center.getZ()-SEARCH_RANGE+column/WIDTH;
                refreshColumn(x,z);
            }
            FallImpactDroplets.along(client,true,FALLS.values());
            var nearby=new ArrayList<Border>();
            for(var borders:COLUMNS.values())for(var border:borders)
                if(Vec3.atCenterOf(border.pool).distanceToSqr(eye)<RANGE*RANGE && validBorder(world,border))nearby.add(border);
            nearby.sort(Comparator.comparingDouble(b->Vec3.atCenterOf(b.pool).distanceToSqr(eye)));
            int budget=144;
            int warmBudget=96;
            var activeNow=new HashSet<Border>();
            float rate=client.options.particles().get()==ParticleStatus.DECREASED?0.36F:0.72F;
            for(int i=0;i<Math.min(256,nearby.size());i++) {
                if(naturality.client.fluids.shared.FallingFluidTail.FallParticleBudget.exhausted(world))break;
                var border=nearby.get(i);
                double density=FallParticleDensity.multiplier(Vec3.atCenterOf(border.pool).distanceTo(eye));
                if(density>=0.12 && (ACTIVE.contains(border) || warmBudget>0)) {
                    activeNow.add(border);
                    if(!ACTIVE.contains(border) && warmBudget>0) {
                        for(int age=1;age<48 && warmBudget>0;age++)if(RANDOM.nextFloat()<rate*density) {
                            spawn(client,border,RANDOM.nextFloat()<0.3F,age);
                            warmBudget--;
                        }
                    }
                }
                if(budget<=0)continue;
                if(RANDOM.nextFloat()>=rate*density)continue;
                boolean big=RANDOM.nextFloat()<0.3F;
                spawn(client,border,big,0);
                if(big)lastBigSpawns++;else lastSmallSpawns++;
                budget--;
            }
            ACTIVE.clear();ACTIVE.addAll(activeNow);
        }
        private static void spawn(Minecraft client,Border border,boolean big,int elapsedTicks) {
            var side=border.side;
            var neighbor=border.receiving();
            var world = LavafallSplash.world;
            if (world == null) return;
            var surface=LavaSurface.at(world,neighbor);
            if (surface == null) return;
            double tangent=0.08+RANDOM.nextDouble()*0.84;
            double u=side==Direction.EAST?0.03:side==Direction.WEST?0.97:tangent;
            double v=side==Direction.SOUTH?0.03:side==Direction.NORTH?0.97:tangent;
            double speed=0.018+RANDOM.nextDouble()*0.025;
            if(elapsedTicks==0 && FallImpactDroplets.vertical(world,border.pool,true))
                FallImpactDroplets.spawn(client,true,side,neighbor.getX()+u,surface.height(u,v),neighbor.getZ()+v);
            // Distance and particle settings are handled above, avoiding the vanilla spawn cutoff.
            var particle=client.particleEngine.createParticle(big?NaturalityParticles.LAVAFALL_BIG:NaturalityParticles.LAVAFALL,
                neighbor.getX()+u,surface.height(u,v)+0.02,neighbor.getZ()+v,
                side.getStepX()*speed+(RANDOM.nextDouble()-0.5)*0.012,0,
                side.getStepZ()*speed+(RANDOM.nextDouble()-0.5)*0.012);
            if(particle instanceof naturality.client.fluids.shared.FallingFluidTail.BorderSplashParticle splash) {
                splash.naturality$enlargeAtRestingEdge(FallParticleDensity.smallFallSize(world,border.pool,true));
                if(elapsedTicks>0)splash.naturality$warmStart(elapsedTicks);
            }
        }
    }

    /** Heavy molten droplets live until contact, never inheriting water's baked blue. */
    public static final class LavaSplashParticle extends SingleQuadParticle implements ImpactDroplet {
        private boolean impactRing=true;
        @Override public void naturality$impactRing(boolean enabled){impactRing=enabled;}
        public static void initialize(){ParticleProviderRegistry.getInstance().register(NaturalityParticles.LAVA_SPLASH,sprites->
            (_,level,x,y,z,vx,vy,vz,_)->new LavaSplashParticle(level,x,y,z,vx,vy,vz,sprites.first()));}
        private LavaSplashParticle(ClientLevel level,double x,double y,double z,double vx,double vy,double vz,TextureAtlasSprite sprite) {
            super(level,x,y,z,sprite);setSize(.03F,.03F);quadSize=.06F;
            xd=vx;yd=vy;zd=vz;
            int tint=LavaEffects.brightColor();setColor(ARGB.red(tint)/255F,ARGB.green(tint)/255F,ARGB.blue(tint)/255F);
        }
        @Override public void naturality$landBeforeExpiring(){}
        @Override public void tick() {
            xo=x;yo=y;zo=z;yd-=.025;move(xd,yd,zd);xd*=.98;yd*=.98;zd*=.98;
            if(LiquidDropletContact.impact(level,true,xo,yo,zo,x,y,z,impactRing) || onGround || y<level.getMinY()-16)remove();
            var pos=BlockPos.containing(x,y,z);var fluid=level.getFluidState(pos);
            if((fluid.is(FluidTags.WATER)||fluid.is(FluidTags.LAVA)) && yd<0 && y<pos.getY()+fluid.getHeight(level,pos))remove();
        }
        @Override protected Layer getLayer(){return Layer.TRANSLUCENT;}
    }

    /** Short upward/outward splash plumes using the supplied small and large sprite sequences. */
    public static final class LavafallParticle extends SingleQuadParticle implements BorderSplashParticle {
        private final FabricSpriteSet sprites;
        private final float initialSize;
        private float borderSizeMultiplier=1;
        private int warmFadeTicks;
        private naturality.client.fluids.shared.FallingFluidTail.@org.jspecify.annotations.Nullable FallSurfacePath stream;
        @Override public void naturality$followStream(){stream=new naturality.client.fluids.shared.FallingFluidTail.FallSurfacePath(true);}
        @Override public void naturality$enlargeAtRestingEdge(float fallSize){borderSizeMultiplier=2*fallSize;}
        @Override public int naturality$lifetime(){return lifetime;}
        @Override public void naturality$warmStart(int elapsedTicks) {
            for(int i=0;i<Math.min(elapsedTicks,lifetime-2);i++)tick();
            warmFadeTicks=4;
            alpha=0;
        }
        public static void initialize() {
            var registry=ParticleProviderRegistry.getInstance();
            registry.register(NaturalityParticles.LAVAFALL,sprites->
                (_,level,x,y,z,vx,vy,vz,random)->new LavafallParticle(level,x,y,z,vx,vy,vz,sprites,random,false));
            registry.register(NaturalityParticles.LAVAFALL_BIG,sprites->
                (_,level,x,y,z,vx,vy,vz,random)->new LavafallParticle(level,x,y,z,vx,vy,vz,sprites,random,true));
        }
        private LavafallParticle(ClientLevel level,double x,double y,double z,double vx,double vy,double vz,
                                  FabricSpriteSet sprites,RandomSource random,boolean big) {
            super(level,x,y,z,sprites.first());
            this.sprites=sprites;
            lifetime=Math.max(1,sprites.getSprites().size())*4;
            initialSize=(0.15F+random.nextFloat()*0.05F)*(big?2.5F:1);
            quadSize=initialSize;
            xd=vx; yd=vy+(big?0.09:0.055)+random.nextDouble()*0.03; zd=vz;
            setSize(0.08F,0.08F);
            alpha=0.8F;
        }
        @Override public void tick() {
            xo=x;yo=y;zo=z;
            if(++age>=lifetime) {remove();return;}
            var currentStream = stream;
            var position=currentStream==null?null:currentStream.step(level,x,y,z,xd,yd,zd);
            if(position==null)move(xd,yd,zd);else setPos(position.x,position.y,position.z);
            xd*=0.96;zd*=0.96;yd=yd*0.96-0.004;
            var frames=sprites.getSprites();
            if(!frames.isEmpty())setSprite(frames.get(Math.min(frames.size()-1,age/4)));
            if(warmFadeTicks>0)alpha=0.8F*(5-warmFadeTicks--)/4;
        }
        @Override public java.util.Optional<net.minecraft.core.particles.ParticleLimit> getParticleLimit() {return FallParticleBudget.OPTIONAL_LIMIT;}
        @Override public float getQuadSize(float partial) {return initialSize*borderSizeMultiplier;}
        @Override protected Layer getLayer(){return Layer.TRANSLUCENT;}
    }
}
