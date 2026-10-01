package naturality.client.fluid;

import java.util.*;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.renderpearl.api.pipeline.*;
import naturality.Naturality;
import naturality.client.portal.*;
import naturality.config.NaturalityConfig;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.oit.OitPipelineSet;
import net.minecraft.client.renderer.state.level.*;
import net.minecraft.core.BlockPos;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.phys.Vec3;

/** Model cross-section foam, rebuilt from the current pose and local fluid geometry every frame. */
public final class LavaIntersection extends QuadParticleRenderState {
    private static final RenderPipeline.Snippet EFFECT=RenderPipeline.builder()
        .withVertexShader(Naturality.id("core/water_intersection"))
        .withFragmentShader(Naturality.id("core/water_intersection")).withCull(false).buildSnippet();
    private static final DepthStencilState DEPTH=new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL,false);
    private static final RenderPipeline PIPELINE=RenderPipelines.register(RenderPipeline.builder(RenderPipelines.PARTICLE_SNIPPET,EFFECT)
        .withLocation(Naturality.id("pipeline/lava_intersection")).withDepthStencilState(DEPTH)
        .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT)).build());
    private static final OitPipelineSet OIT=RenderPipelines.register(OitPipelineSet.builder("naturality_lava_intersection",
        RenderPipeline.builder(RenderPipelines.OIT_PARTICLE_SNIPPET,EFFECT))
        .withDepthBoundsModifier(b->b.withDepthStencilState(DEPTH))
        .withTransmittanceModifier(b->b.withDepthStencilState(DEPTH))
        .withAccumulateModifier(b->b.withDepthStencilState(DEPTH)).build());
    private static final SingleQuadParticle.Layer LAYER=new SingleQuadParticle.Layer(true,naturality.client.AtlasLocations.BLOCKS,PIPELINE,OIT);
    private static final DepthStencilState SPLASH_DEPTH=new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL,true);
    private static final RenderPipeline SPLASH_PIPELINE=RenderPipelines.register(RenderPipeline.builder(RenderPipelines.PARTICLE_SNIPPET,EFFECT)
        .withLocation(Naturality.id("pipeline/lava_splash_depth")).withDepthStencilState(SPLASH_DEPTH)
        .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT)).build());
    private static final OitPipelineSet SPLASH_OIT=RenderPipelines.register(OitPipelineSet.builder("naturality_lava_splash_depth",
        RenderPipeline.builder(RenderPipelines.OIT_PARTICLE_SNIPPET,EFFECT))
        .withDepthBoundsModifier(b->b.withDepthStencilState(SPLASH_DEPTH))
        .withTransmittanceModifier(b->b.withDepthStencilState(SPLASH_DEPTH))
        .withAccumulateModifier(b->b.withDepthStencilState(SPLASH_DEPTH)).build());
    private static final SingleQuadParticle.Layer SPLASH_LAYER=new SingleQuadParticle.Layer(true,naturality.client.AtlasLocations.BLOCKS,SPLASH_PIPELINE,SPLASH_OIT);
    private record Wake(BlockPos pos,int x,int z,float alpha,long tick) {}
    private static final Map<String,Wake> WAKES=new HashMap<>();
    private static @org.jspecify.annotations.Nullable ClientLevel wakeWorld;
    private record Pixel(Vec3 a,Vec3 b,Vec3 c,Vec3 d,int color,int light) {}
    private static final class Tile {
        final LavaSurface surface;
        final List<PortalGlowOcclusion.Rect> first=new ArrayList<>(),second=new ArrayList<>();
        Tile(LavaSurface surface) {this.surface=surface;}
    }
    private final List<Pixel> pixels=new ArrayList<>();
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
            var mesh=PortalModelCapture.mesh(entity,origin,camera,partial);
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
    private static List<PortalGlowOcclusion.Rect> worldSections(List<Vec3[]> mesh,double h,double dx,double dz,
                                                               double x,double z,Vec3 origin) {
        var local=WaterIntersectionGeometry.section(mesh,h,dx,dz,x-0.25,z-0.25,x+1.25,z+1.25);
        var result=new ArrayList<PortalGlowOcclusion.Rect>();
        for(var r:local) result.add(new PortalGlowOcclusion.Rect(r.left()+origin.x,r.bottom()+origin.z,r.right()+origin.x,r.top()+origin.z));
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
            pixels.add(new Pixel(vertex(s,x/16.0,z/16.0,eye),vertex(s,x/16.0,(z+1)/16.0,eye),
                vertex(s,(x+1)/16.0,(z+1)/16.0,eye),vertex(s,(x+1)/16.0,z/16.0,eye),color,light));
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
        var draws=layer==SPLASH_LAYER?splashPixels:layer==LAYER?pixels:List.<Pixel>of();
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
            pixels.add(new Pixel(vertex(surface,w.x/16.0,w.z/16.0,eye),vertex(surface,w.x/16.0,(w.z+1)/16.0,eye),
                vertex(surface,(w.x+1)/16.0,(w.z+1)/16.0,eye),vertex(surface,(w.x+1)/16.0,w.z/16.0,eye),color,light));
        }
    }
    void ripplePixel(LavaSurface surface,int x,int z,Vec3 eye,float alpha,int tint,int light) {
        float r=ARGB.red(tint)/255F;
        float g=ARGB.green(tint)/255F;
        float b=ARGB.blue(tint)/255F;
        pixels.add(new Pixel(vertex(surface,x/16.0,z/16.0,eye),vertex(surface,x/16.0,(z+1)/16.0,eye),
            vertex(surface,(x+1)/16.0,(z+1)/16.0,eye),vertex(surface,(x+1)/16.0,z/16.0,eye),
            ARGB.colorFromFloat(alpha,r,g,b),light));
    }
    private static Vec3 vertex(LavaSurface s,double x,double z,Vec3 eye) {
        return new Vec3(s.pos().getX()-eye.x+x,s.height(x,z)-eye.y+1.0/1024,s.pos().getZ()-eye.z+z);
    }
    @Override public void submit(SubmitNodeCollector collector,CameraRenderState camera){if(!isEmpty())collector.submitQuadParticleGroup(this);}
}




