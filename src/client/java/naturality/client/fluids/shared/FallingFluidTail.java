package naturality.client.fluids.shared;

import com.mojang.blaze3d.vertex.VertexConsumer;
import naturality.config.live.NaturalityConfig;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.client.renderer.block.FluidRenderer;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import naturality.client.fluids.shared.FluidGeometry.LiquidSurfaceGeometry;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;
import naturality.client.fluids.lava.LavaEffects.LavaSurface;
import naturality.client.fluids.water.WaterShaders.WaterSurface;
import java.util.concurrent.ThreadLocalRandom;
import naturality.bootstrap.ContentRegistration.NaturalityParticles;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.tags.FluidTags;
import naturality.client.fluids.lava.LavaIntersection.LavaRipples;
import naturality.client.fluids.water.WaterIntersection.WaterRipples;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.core.particles.ParticleLimit;
import net.minecraft.core.particles.ParticleOptions;

/** A capless continuation of a falling liquid into air, with shader-stepped opacity. */
@org.jspecify.annotations.NullMarked
public final class FallingFluidTail {
    // Lightmap coordinates occupy only the low byte; the vertex shader strips this flag.
    public static final int LIGHT_FLAG=0x1000;
    public static final float UNDERSIDE_OFFSET=0.5F;
    @FunctionalInterface public interface LightSampler {
        int sample(float x,float y,float z,int fallback);
    }
    private FallingFluidTail() {}

    public static boolean eligible(BlockAndTintGetter level,BlockPos pos,FluidState fluid) {
        if (!naturality.config.startup.FeatureConfig.Feature.FLUID_TAILS.enabled()) return false;
        var settings=NaturalityConfig.get().liquids;
        boolean water=fluid.getType().isSame(Fluids.WATER),lava=fluid.getType().isSame(Fluids.LAVA);
        return ((water && settings.water) || (lava && settings.lava))
            && fluid.getValue(FlowingFluid.FALLING) && level.getBlockState(pos.below()).isAir();
    }

    public static void emit(BlockAndTintGetter level,BlockPos pos,BlockState block,FluidState fluid,
                            FluidModel model,FluidRenderer.Output output,LightSampler lighting) {
        if(!eligible(level,pos,fluid))return;
        // Lava normally uses the solid layer; this geometry needs true translucency.
        var vertices=output.getBuilder(ChunkSectionLayer.TRANSLUCENT);
        var sprite=model.flowingMaterial().sprite();
        var tintSource = model.tintSource();
        int tint=tintSource==null?-1:tintSource.colorInWorld(block,level,pos);
        float x=pos.getX()&15,y=pos.getY()&15,z=pos.getZ()&15;
        for(var side:Direction.Plane.HORIZONTAL) {
            if(level.getFluidState(pos.relative(side)).getType().isSame(fluid.getType()))continue;
            if(level.getBlockState(pos.below().relative(side)).isSolidRender())continue;
            float x0=x,x1=x+1,z0=z,z1=z+1;
            switch(side) {
                case NORTH -> {z0=z1=z+0.001F;}
                case SOUTH -> {z0=z1=z+0.999F;x0=x+1;x1=x;}
                case WEST -> {x0=x1=x+0.001F;z0=z+1;z1=z;}
                case EAST -> {x0=x1=x+0.999F;}
                default -> throw new IllegalStateException();
            }
            x0=FluidGeometry.connectedCoordinate(level,pos,x0,true);
            x1=FluidGeometry.connectedCoordinate(level,pos,x1,true);
            z0=FluidGeometry.connectedCoordinate(level,pos,z0,false);
            z1=FluidGeometry.connectedCoordinate(level,pos,z1,false);
            var cardinal=level.cardinalLighting();
            float shade=cardinal.up()*(side.getAxis()==Direction.Axis.Z?cardinal.north():cardinal.west());
            int color=ARGB.scaleRGB(tint,shade);
            int light=fluid.getType().isSame(Fluids.LAVA) && NaturalityConfig.get().liquids.emissiveLava
                ?LightCoordsUtil.FULL_BRIGHT:LightCoordsUtil.max(LightCoordsUtil.getLightCoords(level,pos),LightCoordsUtil.getLightCoords(level,pos.below()));
            // Vanilla flowing side UVs cover half of the 32-pixel flowing sprite.
            float u0=sprite.getU(0),u1=sprite.getU(0.5F),v0=sprite.getV(0),v1=sprite.getV(0.5F);
            vertex(vertices,x0,y,z0,color,u0,v0,light,lighting);
            vertex(vertices,x1,y,z1,color,u1,v0,light,lighting);
            vertex(vertices,x1,y-1,z1,color,u1,v1,light,lighting);
            vertex(vertices,x0,y-1,z0,color,u0,v1,light,lighting);
            vertex(vertices,x0,y,z0,color,u0,v0,light,lighting);
            vertex(vertices,x0,y-1,z0,color,u0,v1,light,lighting);
            vertex(vertices,x1,y-1,z1,color,u1,v1,light,lighting);
            vertex(vertices,x1,y,z1,color,u1,v0,light,lighting);
        }
        // Preserve vanilla's downward-facing still-texture underside, recessed
        // half a block into the real falling column instead of capping its tail.
        var underside=model.stillMaterial().sprite();
        var capVertices=output.getBuilder(model.layer());
        float capY=y+UNDERSIDE_OFFSET-0.001F;
        int capColor=ARGB.scaleRGB(tint,level.cardinalLighting().down());
        int capLight=LightCoordsUtil.getLightCoords(level,pos);
        capVertex(capVertices,x,capY,z,capColor,underside.getU0(),underside.getV0(),capLight,lighting);
        capVertex(capVertices,x+1,capY,z,capColor,underside.getU1(),underside.getV0(),capLight,lighting);
        capVertex(capVertices,x+1,capY,z+1,capColor,underside.getU1(),underside.getV1(),capLight,lighting);
        capVertex(capVertices,x,capY,z+1,capColor,underside.getU0(),underside.getV1(),capLight,lighting);
    }
    private static void vertex(VertexConsumer vertices,float x,float y,float z,int color,float u,float v,int light,LightSampler lighting) {
        vertices.addVertex(x,y,z,color,u,v,OverlayTexture.NO_OVERLAY,lighting.sample(x,y,z,light)|LIGHT_FLAG,0,1,0);
    }
    private static void capVertex(VertexConsumer vertices,float x,float y,float z,int color,float u,float v,int light,LightSampler lighting) {
        vertices.addVertex(x,y,z,color,u,v,OverlayTexture.NO_OVERLAY,lighting.sample(x,y,z,light),0,1,0);
    }

    /** Spray falls freely until it reaches the stream, then follows its slopes and step edges. */
    public static final class FallSurfacePath {
        private final boolean lava;
        private boolean following;
        private double dx,dz;
        public FallSurfacePath(boolean lava){this.lava=lava;}
        public @org.jspecify.annotations.Nullable Vec3 step(ClientLevel level,double x,double y,double z,double vx,double vy,double vz) {
            double speed=lava?.045:.18;
            var current=following?LiquidSurfaceGeometry.near(level,x,y,z,lava,true):null;
            if(!following) {
                var landing=LiquidSurfaceGeometry.near(level,x+vx,y+vy,z+vz,lava,true);
                if(landing==null || vy>0 || y<landing.height()-.3 || y+vy>landing.height()+.035)return null;
                following=true;
                current=landing;
                double length=Math.hypot(vx,vz);
                if(length>.0001){dx=vx/length;dz=vz/length;}
            }
            if(current!=null && Math.abs(current.height()-y)<1.5) {
                var flow=level.getFluidState(current.pos()).getFlow(level,current.pos());
                double flowLength=Math.hypot(flow.x,flow.z);
                double length=Math.hypot(current.downhillX(),current.downhillZ());
                if(flowLength>.0001){dx=flow.x/flowLength;dz=flow.z/flowLength;}
                else if(length>.0001){dx=current.downhillX()/length;dz=current.downhillZ()/length;}
                x=current.x();z=current.z();
            }
            double nx=x+dx*speed,nz=z+dz*speed;
            // At a ledge the next tile must win; choosing the previous tile's
            // clamped edge repeatedly can trap slower lava spray at the boundary.
            var next=LiquidSurfaceGeometry.near(level,nx,y,nz,lava,false);
            if(next!=null && next.height()<=y+.25 && next.height()>=y-1.25)
                return new Vec3(next.x(),Math.max(next.height()+.035,y-speed*1.5),next.z());
            if(level.getBlockState(BlockPos.containing(nx,y-speed,nz)).isSolidRender()) {following=false;return null;}
            return new Vec3(nx,y-speed,nz);
        }
    }

    /** Ballistic droplets and animated spray along exposed vertical falls and their feet. */
    public static final class FallImpactDroplets {
        private static final Direction[] HORIZONTAL=Direction.Plane.HORIZONTAL.stream().toArray(Direction[]::new);
        private static final ActiveFaces WATER_ACTIVE=new ActiveFaces(), LAVA_ACTIVE=new ActiveFaces();

        /** Membership only: four exposed sides share one packed position, with reusable tick buffers. */
        static final class ActiveFaces {
            private Long2ByteOpenHashMap previous=new Long2ByteOpenHashMap(), next=new Long2ByteOpenHashMap();
            void begin(){next.clear();}
            boolean contains(long position,int side){return (previous.get(position)&(1<<side))!=0;}
            void add(long position,int side){next.put(position,(byte)(next.get(position)|(1<<side)));}
            void end(){var swap=previous;previous=next;next=swap;}
            void reset(){previous.clear();next.clear();}
        }

        private FallImpactDroplets() {}
        public static void reset(boolean lava) {(lava?LAVA_ACTIVE:WATER_ACTIVE).reset();}
        public static boolean vertical(ClientLevel level,BlockPos foot,boolean lava) {
            var above=level.getFluidState(foot.above());
            return above.is(lava?FluidTags.LAVA:FluidTags.WATER)
                && !above.isSource() && above.getValue(FlowingFluid.FALLING);
        }
        public static boolean falling(ClientLevel level,BlockPos pos,boolean lava) {
            var fluid=level.getFluidState(pos);
            return fluid.is(lava?FluidTags.LAVA:FluidTags.WATER)
                && !fluid.isSource() && fluid.getValue(FlowingFluid.FALLING);
        }
        public static void along(Minecraft client,boolean lava,java.util.Collection<java.util.List<BlockPos>> columns) {
            var level = client.level;
            if (level == null) return;
            var random=ThreadLocalRandom.current();
            var eye=client.gameRenderer.mainCamera().position();
            double rate=client.options.particles().get()==net.minecraft.server.level.ParticleStatus.DECREASED?.01:.02;
            int budget=128;
            int warmBudget=32;
            var previous=lava?LAVA_ACTIVE:WATER_ACTIVE;
            previous.begin();
            var neighbor=new BlockPos.MutableBlockPos();
            for(var positions:columns)for(var pos:positions) {
                if(naturality.client.fluids.shared.FallingFluidTail.FallParticleBudget.exhausted(level))break;
                double distance=net.minecraft.world.phys.Vec3.atCenterOf(pos).distanceTo(eye);
                double density=FallParticleDensity.multiplier(distance);
                if(density<=0 || !naturality.api.worlds.LoadedChunks.has(level, pos) || !falling(level,pos,lava))continue;
                boolean emit=random.nextDouble()<rate*density;
                long position=pos.asLong();
                // Keep animated spray tucked against exposed sides and carry it down with the stream.
                for(int sideIndex=0;sideIndex<HORIZONTAL.length;sideIndex++) {
                    var side=HORIZONTAL[sideIndex];
                    neighbor.setWithOffset(pos,side);
                    if(!naturality.api.worlds.LoadedChunks.has(level, neighbor) || !level.getBlockState(neighbor).isAir())continue;
                    boolean wasActive=previous.contains(position,sideIndex);
                    if(density>=0.12 && (wasActive || warmBudget>0)) {
                        previous.add(position,sideIndex);
                        if(!wasActive && warmBudget>0
                            && random.nextDouble()<Math.min(1,rate*density*(lava?48:24))) {
                            spawnAlong(client,lava,pos,side,1+random.nextInt(lava?47:23));
                            warmBudget--;
                        }
                    }
                    if(!emit || budget<=0)continue;
                    spawnAlong(client,lava,pos,side,0);
                    budget--;
                }
            }
            previous.end();
        }
        private static void spawnAlong(Minecraft client,boolean lava,BlockPos pos,Direction side,int elapsedTicks) {
            var level = client.level;
            if (level == null) return;
                    var random=ThreadLocalRandom.current();
                    double tangent=random.nextDouble()*.96+.02;
                    double x=pos.getX()+(side==Direction.EAST?1.015:side==Direction.WEST?-.015:tangent);
                    double z=pos.getZ()+(side==Direction.SOUTH?1.015:side==Direction.NORTH?-.015:tangent);
                    WaterSurface waterSurface=lava?null:WaterSurface.at(level,pos);
                    LavaSurface lavaSurface=lava?LavaSurface.at(level,pos):null;
                    double localX=Math.clamp(x-pos.getX(),0,1),localZ=Math.clamp(z-pos.getZ(),0,1);
                    double y=waterSurface!=null?waterSurface.height(localX,localZ)+.025
                        :lavaSurface!=null?lavaSurface.height(localX,localZ)+.025:pos.getY()+random.nextDouble();
                    boolean big=random.nextDouble()<.3;
                    var sprayType=lava
                        ? (big?NaturalityParticles.LAVAFALL_BIG:NaturalityParticles.LAVAFALL)
                        : (big?NaturalityParticles.WATERFALL_BIG:NaturalityParticles.WATERFALL);
                    double speedScale=lava?.5:2;
                    var spray=client.particleEngine.createParticle(sprayType,x,y,z,0,0,0);
                    if(spray==null)return;
                    double gx=waterSurface!=null?waterSurface.height(.75,.5)-waterSurface.height(.25,.5)
                        :lavaSurface!=null?lavaSurface.height(.75,.5)-lavaSurface.height(.25,.5):0;
                    double gz=waterSurface!=null?waterSurface.height(.5,.75)-waterSurface.height(.5,.25)
                        :lavaSurface!=null?lavaSurface.height(.5,.75)-lavaSurface.height(.5,.25):0;
                    double slope=Math.hypot(gx,gz),spraySpeed;
                    if(spray!=null && slope>.025) {
                        // Follow the downhill tangent of the rendered fluid surface.
                        spraySpeed=(.018+random.nextDouble()*.025)*speedScale;
                        double vx=-gx/slope*spraySpeed,vz=-gz/slope*spraySpeed;
                        spray.setParticleSpeed(vx,-slope*spraySpeed-.004,vz);
                    } else if(spray!=null) {
                        spraySpeed=(.002+random.nextDouble()*.006)*speedScale;
                        spray.setParticleSpeed(side.getStepX()*spraySpeed,
                            (-.035-random.nextDouble()*.025)*speedScale*(lava?1:3),side.getStepZ()*spraySpeed);
                    }
                    if(spray instanceof BorderSplashParticle splash) {
                        splash.naturality$followStream();
                        if(elapsedTicks>0)splash.naturality$warmStart(elapsedTicks);
                    }
        }
        public static void spawn(Minecraft client,boolean lava,Direction outward,double x,double y,double z) {
            var level = client.level;
            if (level == null) return;
            var config = naturality.config.live.NaturalityConfig.get().liquids;
            if (lava ? !config.lavaFallDroplets : !config.waterFallDroplets) return;
            var random=ThreadLocalRandom.current();
            for(int i=0,count=2+random.nextInt(3);i<count;i++) {
                if(!naturality.client.fluids.shared.FallingFluidTail.FallParticleBudget.allowDroplet(level))return;
                var particle=client.particleEngine.createParticle(lava?NaturalityParticles.LAVA_SPLASH:ParticleTypes.SPLASH,
                    x,y+.06,z,0,0,0);
                if(particle instanceof ImpactDroplet drop) {
                    drop.naturality$landBeforeExpiring();
                    drop.naturality$impactRing(random.nextDouble()<.15);
                }
                double speed=.045+random.nextDouble()*.095;
                if(particle!=null)particle.setParticleSpeed(
                    outward.getStepX()*speed+(random.nextDouble()-.5)*.07,
                    .10+random.nextDouble()*.16,
                    outward.getStepZ()*speed+(random.nextDouble()-.5)*.07);
            }
        }
    }

    /** Shared spawn-density falloff for waterfall and lavafall spray. */
    public static final class FallParticleDensity {
        public static final int RANGE=72;
        private FallParticleDensity() {}
        public static double multiplier(double distance) {
            double t=Math.clamp((distance-36)/36,0,1);
            return 1-t*t*(3-2*t);
        }
        /** Search the columns closest to the camera first after entering a new area. */
        public static int[] scanOrder(int radius) {
            int width=radius*2+1;
            int[] order=new int[width*width];
            int count=0;
            for(int ring=0;ring<=radius;ring++)for(int z=-ring;z<=ring;z++)for(int x=-ring;x<=ring;x++)
                if(Math.max(Math.abs(x),Math.abs(z))==ring)
                    order[count++]=(z+radius)*width+x+radius;
            return order;
        }
        public static float smallFallSize(net.minecraft.client.multiplayer.ClientLevel level,
                                         net.minecraft.core.BlockPos start,boolean lava) {
            var tag=lava?net.minecraft.tags.FluidTags.LAVA:net.minecraft.tags.FluidTags.WATER;
            if(level.getFluidState(start).is(tag) && level.getFluidState(start).isSource())start=start.above();
            int flowing=0;
            for(int dy=0;dy<10;dy++) {
                var fluid=level.getFluidState(start.above(dy));
                if(!fluid.is(tag) || fluid.isSource())break;
                flowing++;
            }
            return (float)Math.clamp(.35+flowing*.08,.35,1.0);
        }
    }

    /** Opposite liquid quenches the droplet; it never produces an impact ring. */
    public static final class LiquidDropletContact {
        public static boolean impact(ClientLevel level,boolean lava,double x0,double y0,double z0,double x,double y,double z) {
            return impact(level,lava,x0,y0,z0,x,y,z,true);
        }
        public static boolean impact(ClientLevel level,boolean lava,double x0,double y0,double z0,double x,double y,double z,boolean ring) {
            boolean opposite=lava?WaterRipples.impact(level,x0,y0,z0,x,y,z,false):LavaRipples.impact(level,x0,y0,z0,x,y,z,false);
            if(opposite) {
                var smoke=net.minecraft.client.Minecraft.getInstance().particleEngine.createParticle(ParticleTypes.SMOKE,x,y+.04,z,0,.015,0);
                if(smoke!=null)smoke.scale(.35F);
                return true;
            }
            return lava?LavaRipples.impact(level,x0,y0,z0,x,y,z,ring):WaterRipples.impact(level,x0,y0,z0,x,y,z,ring);
        }
    }

    /** Palette presence is conservative; cache it only across one synchronous scan batch. */
    public static final class FluidSectionScan {
        private final Long2ByteOpenHashMap water = new Long2ByteOpenHashMap();
        private final Long2ByteOpenHashMap lava = new Long2ByteOpenHashMap();
        public void reset() { water.clear(); lava.clear(); }
        public boolean contains(LevelChunk chunk, int sectionY, boolean molten) {
            var cache = molten ? lava : water;
            long key = SectionPos.asLong(chunk.getPos().x(), sectionY, chunk.getPos().z());
            byte cached = cache.get(key);
            if (cached != 0) return cached == 1;
            int index = chunk.getSectionIndexFromSectionY(sectionY);
            boolean found = index >= 0 && index < chunk.getSectionsCount()
                && chunk.getSection(index).maybeHas(state -> state.getFluidState().is(molten ? FluidTags.LAVA : FluidTags.WATER));
            cache.put(key, found ? (byte)1 : (byte)2);
            return found;
        }
    }

    /** Water and lava share a population limit, including queued particles and warm starts. */
    public static final class FallParticleBudget {
        public static final ParticleLimit LIMIT=new ParticleLimit(768);
        public static final java.util.Optional<ParticleLimit> OPTIONAL_LIMIT=java.util.Optional.of(LIMIT);
        private static @org.jspecify.annotations.Nullable ClientLevel world;
        private static long tick=Long.MIN_VALUE;
        private static int spawned,droplets;
        private FallParticleBudget() { }
        public static boolean includes(ParticleOptions options) {
            var type=options.getType();
            return type==NaturalityParticles.WATERFALL || type==NaturalityParticles.WATERFALL_BIG
                || type==NaturalityParticles.LAVAFALL || type==NaturalityParticles.LAVAFALL_BIG;
        }
        public static boolean exhausted(ClientLevel level) {
            if(world!=level || tick!=level.getGameTime()) {world=level;tick=level.getGameTime();spawned=droplets=0;}
            return spawned>=64;
        }
        public static void admitted(ClientLevel level) {exhausted(level);spawned++;}
        public static boolean allowDroplet(ClientLevel level) {
            exhausted(level);
            if(droplets>=64)return false;
            droplets++;return true;
        }
        public static void reset() {world=null;tick=Long.MIN_VALUE;spawned=droplets=0;}
    }

    /** Only droplets launched by a large impact opt out of vanilla's short timer. */
    public interface ImpactDroplet {
        void naturality$landBeforeExpiring();
        default void naturality$impactRing(boolean enabled) {}
        default void naturality$waterDroplet() {}
        default void naturality$rainSplash() {}
    }

    /** Marks waterfall particles whose spawning location is a flowing-to-resting edge. */
    public interface BorderSplashParticle {
        void naturality$enlargeAtRestingEdge(float fallSize);
        int naturality$lifetime();
        void naturality$warmStart(int elapsedTicks);
        void naturality$followStream();
    }
}
