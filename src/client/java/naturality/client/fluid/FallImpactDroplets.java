package naturality.client.fluid;

import java.util.concurrent.ThreadLocalRandom;
import java.util.HashSet;
import java.util.Set;
import naturality.NaturalityParticles;
import naturality.client.particle.BorderSplashParticle;
import naturality.client.particle.ImpactDroplet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.material.FlowingFluid;

/** Ballistic droplets and animated spray along exposed vertical falls and their feet. */
public final class FallImpactDroplets {
    private record Face(BlockPos pos,Direction side) {}
    private static final Set<Face> WATER_ACTIVE=new HashSet<>(), LAVA_ACTIVE=new HashSet<>();
    private FallImpactDroplets() {}
    public static void reset(boolean lava) {(lava?LAVA_ACTIVE:WATER_ACTIVE).clear();}
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
        var activeNow=new HashSet<Face>();
        for(var positions:columns)for(var pos:positions) {
            double distance=net.minecraft.world.phys.Vec3.atCenterOf(pos).distanceTo(eye);
            double density=FallParticleDensity.multiplier(distance);
            if(density<=0 || !naturality.util.LoadedChunks.has(level, pos) || !falling(level,pos,lava))continue;
            boolean emit=random.nextDouble()<rate*density;
            // Keep animated spray tucked against exposed sides and carry it down with the stream.
            for(var side:Direction.Plane.HORIZONTAL) {
                var neighbor=pos.relative(side);
                if(!naturality.util.LoadedChunks.has(level, neighbor) || !level.getBlockState(neighbor).isAir())continue;
                var face=new Face(pos,side);
                if(density>=0.12 && (previous.contains(face) || warmBudget>0)) {
                    activeNow.add(face);
                    if(!previous.contains(face) && warmBudget>0
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
        previous.clear();previous.addAll(activeNow);
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
                if(elapsedTicks>0 && spray instanceof BorderSplashParticle splash)
                    splash.naturality$warmStart(elapsedTicks);
    }
    public static void spawn(Minecraft client,boolean lava,Direction outward,double x,double y,double z) {
        var level = client.level;
        if (level == null) return;
        var config = naturality.config.NaturalityConfig.get().liquids;
        if (lava ? !config.lavaFallDroplets : !config.waterFallDroplets) return;
        var random=ThreadLocalRandom.current();
        for(int i=0,count=2+random.nextInt(3);i<count;i++) {
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


