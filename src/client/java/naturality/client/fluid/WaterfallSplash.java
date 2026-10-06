package naturality.client.fluid;

import java.util.*;
import naturality.NaturalityParticles;
import naturality.config.NaturalityConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ParticleStatus;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.phys.Vec3;

/** Bounded search for descending water surfaces meeting flat, exposed resting water. */
public final class WaterfallSplash {
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
    public static int lastSmallSpawns,lastBigSpawns;
    private WaterfallSplash() {}
    public static boolean impactColumn(ClientLevel level,BlockPos pool) {
        if(!naturality.util.LoadedChunks.has(level, pool))return false;
        var water=level.getFluidState(pool);
        if(!water.is(FluidTags.WATER))return false;
        var surface=WaterSurface.at(level,pool);
        if(surface!=null)return !water.isSource() && relief(surface)>1.0/64;
        var above=level.getFluidState(pool.above());
        return water.isSource() && above.is(FluidTags.WATER) && !above.isSource()
            && above.getValue(FlowingFluid.FALLING);
    }
    private static double relief(WaterSurface s) {
        return Math.max(Math.max(s.nw(),s.ne()),Math.max(s.sw(),s.se()))
            -Math.min(Math.min(s.nw(),s.ne()),Math.min(s.sw(),s.se()));
    }
    private static void refreshColumn(int x,int z) {
        long key=BlockPos.asLong(x,0,z);
        COLUMNS.remove(key);FALLS.remove(key);
        var world = WaterfallSplash.world;
        var center = WaterfallSplash.center;
        if(world == null || center == null || !naturality.util.LoadedChunks.has(world, new BlockPos(x,center.getY(),z)))return;
        var borders=new ArrayList<Border>();
        var falls=new ArrayList<BlockPos>();
        for(int y=center.getY()-SEARCH_RANGE;y<=center.getY()+SEARCH_RANGE;y++) {
            var pool=new BlockPos(x,y,z);
            if(FallImpactDroplets.falling(world,pool,false))falls.add(pool);
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
        if(!naturality.util.LoadedChunks.has(level, neighbor))return false;
        var rest=level.getFluidState(neighbor);
        var receiving=WaterSurface.at(level,neighbor);
        // Spray belongs at the foot of the slope, not its upper junction with
        // the falling column. Even source blocks can have a sloping rendered top.
        if(!rest.is(FluidTags.WATER) || !rest.isSource() || receiving==null)return false;
        var incoming=WaterSurface.at(level,border.pool);
        if(incoming==null)return border.drop==0;
        double dx=border.side.getStepX(),dz=border.side.getStepZ();
        double high=incoming.height(0.5-dx*0.4,0.5-dz*0.4);
        double low=incoming.height(0.5+dx*0.4,0.5+dz*0.4);
        return high-low>1.0/64 && low>=receiving.height(0.5,0.5)-1.0/64;
    }
    public static void tick(Minecraft client) {
        lastSmallSpawns=lastBigSpawns=0;
        if(world!=client.level) {world=client.level;center=null;COLUMNS.clear();FALLS.clear();ACTIVE.clear();FallImpactDroplets.reset(false);cursor=0;}
        var world = WaterfallSplash.world;
        if(client.isPaused())return;
        if(world==null || !NaturalityConfig.get().liquids.water || !NaturalityConfig.get().liquids.waterfallParticles
            || client.options.particles().get()==ParticleStatus.MINIMAL) {COLUMNS.clear();FALLS.clear();ACTIVE.clear();FallImpactDroplets.reset(false);center=null;return;}
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
        var center = java.util.Objects.requireNonNull(WaterfallSplash.center);
        FALLS.keySet().removeIf(key->Math.abs(BlockPos.getX(key)-center.getX())>SEARCH_RANGE || Math.abs(BlockPos.getZ(key)-center.getZ())>SEARCH_RANGE);
        // Keep an eight-block search margin for camera movement; refresh without chunk loads.
        for(int i=0;i<256;i++) {
            int column=SCAN_ORDER[cursor];
            cursor=(cursor+1)%SCAN_ORDER.length;
            int x=center.getX()-SEARCH_RANGE+column%WIDTH,z=center.getZ()-SEARCH_RANGE+column/WIDTH;
            refreshColumn(x,z);
        }
        FallImpactDroplets.along(client,false,FALLS.values());
        var nearby=new ArrayList<Border>();
        for(var borders:COLUMNS.values())for(var border:borders)
            if(Vec3.atCenterOf(border.pool).distanceToSqr(eye)<RANGE*RANGE && validBorder(world,border))nearby.add(border);
        nearby.sort(Comparator.comparingDouble(b->Vec3.atCenterOf(b.pool).distanceToSqr(eye)));
        int budget=144;
        int warmBudget=96;
        var activeNow=new HashSet<Border>();
        float rate=client.options.particles().get()==ParticleStatus.DECREASED?0.36F:0.72F;
        for(int i=0;i<Math.min(256,nearby.size());i++) {
            if(naturality.client.particle.FallParticleBudget.exhausted(world))break;
            var border=nearby.get(i);
            double density=FallParticleDensity.multiplier(Vec3.atCenterOf(border.pool).distanceTo(eye));
            if(density>=0.12 && (ACTIVE.contains(border) || warmBudget>0)) {
                activeNow.add(border);
                if(!ACTIVE.contains(border) && warmBudget>0) {
                    // Approximate the steady-state mix of ages, without replaying droplet impacts.
                    for(int age=1;age<24 && warmBudget>0;age++)if(RANDOM.nextFloat()<rate*density) {
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
        var world = WaterfallSplash.world;
        if (world == null) return;
        var surface=WaterSurface.at(world,neighbor);
        if (surface == null) return;
        double tangent=0.08+RANDOM.nextDouble()*0.84;
        double u=side==Direction.EAST?0.03:side==Direction.WEST?0.97:tangent;
        double v=side==Direction.SOUTH?0.03:side==Direction.NORTH?0.97:tangent;
        double speed=0.018+RANDOM.nextDouble()*0.025;
        if(elapsedTicks==0 && FallImpactDroplets.vertical(world,border.pool,false))
            FallImpactDroplets.spawn(client,false,side,neighbor.getX()+u,surface.height(u,v),neighbor.getZ()+v);
        // Distance and particle settings are handled above, avoiding the vanilla spawn cutoff.
        var particle=client.particleEngine.createParticle(big?NaturalityParticles.WATERFALL_BIG:NaturalityParticles.WATERFALL,
            neighbor.getX()+u,surface.height(u,v)+0.02,neighbor.getZ()+v,
            side.getStepX()*speed+(RANDOM.nextDouble()-0.5)*0.012,0,
            side.getStepZ()*speed+(RANDOM.nextDouble()-0.5)*0.012);
        if(particle instanceof naturality.client.particle.BorderSplashParticle splash) {
            splash.naturality$enlargeAtRestingEdge(FallParticleDensity.smallFallSize(world,border.pool,false));
            if(elapsedTicks>0)splash.naturality$warmStart(elapsedTicks);
        }
    }
}






