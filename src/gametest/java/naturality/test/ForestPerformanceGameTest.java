package naturality.test;

import java.lang.management.ManagementFactory;
import java.util.*;
import naturality.client.portal.PortalClippedCollector;
import naturality.config.GameplaySettings;
import naturality.fire.FireGeometry;
import naturality.snow.SnowGeometry;
import naturality.util.RaycastShapePresence;
import naturality.weather.*;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.renderer.feature.ItemFeatureRenderer;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.*;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.*;

public final class ForestPerformanceGameTest implements FabricClientGameTest {
    @SuppressWarnings("unused") // Volatile benchmark sink prevents dead-code elimination; deliberately never read.
    private static volatile Object sink;
    private static void check(boolean value,String message) {if(!value)throw new AssertionError(message);}
    @Override public void runTest(ClientGameTestContext context) {
        try(var world=TestWorlds.disposable(context).create()) {
            var server=world.getServer();
            server.runCommand("gamemode spectator @a");
            server.runCommand("tp @a 0 130 0");
            server.runCommand("gamerule minecraft:random_tick_speed 0");
            server.runCommand("fill -20 120 -20 20 120 20 stone");
            server.runCommand("fill -16 124 -16 16 124 16 oak_leaves[persistent=true]");
            server.runCommand("fill 14 121 -2 16 121 2 oak_fence");
            server.runCommand("setblock 15 122 0 snow[layers=4]");
            server.runCommand("setblock -16 121 0 fire");
            server.runCommand("setblock -15 121 0 oak_fence");
            server.runOnServer(s -> {
                var profile=new WeatherProfile(true);
                profile.overrideWind=profile.overrideDirection=profile.overrideRain=profile.overrideTemperature=true;
                profile.wind=100;profile.direction=0;profile.rain=0;profile.temperature=0;
                WeatherWorldData.get(s).setProfile("minecraft:overworld",profile);
            });
            world.getConnection().waitForClientboundPackets();context.waitTicks(420);
            context.waitFor(c -> WeatherSystem.state(TestFixtures.level(c))!=null && TestFixtures.require(WeatherSystem.state(TestFixtures.level(c)), "WeatherSystem.state(c.level)").wind()==100);
            server.runOnServer(s -> {compareRays(s.overworld());checkPresence(s.overworld());checkShapes(s.overworld());});
            context.runOnClient(c -> {
                compareRays(c.level);checkPresence(c.level);checkShapes(c.level);
                var probe=new ClipContext(new Vec3(-8.5,123,-8.5),new Vec3(8.5,123,8.5),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,TestFixtures.player(c));
                for(int round=0;round<3;round++) {
                    measure("ray-original",2000,() -> sink=originalRay(c.level,probe));
                    measure("ray-optimized",2000,() -> sink=TestFixtures.level(c).clip(probe));
                }
                System.out.println("FOREST_PERFORMANCE: portal identity, 1536 exact rays, palette lifetime and wind shape variants passed");
            });
        }
    }

    static void checkPortalMetadata() {
        try {
            var current=PortalClippedCollector.class.getDeclaredField("current");current.setAccessible(true);
            Object previous=current.get(null);
            var pose=new com.mojang.blaze3d.vertex.PoseStack().last();
            int[] tint={-1};
            List<BakedQuad> quads=new AbstractList<>() {
                @Override public BakedQuad get(int i) {throw new AssertionError("Clip lookup traversed quads");}
                @Override public int size() {return 4096;}
                @Override public int hashCode() {throw new AssertionError("Clip lookup hashed quads");}
            };
            var a=new PortalClippedCollector.Plane(true,4,1);
            var b=new PortalClippedCollector.Plane(false,8,-1);
            try {
                current.set(null,a);
                var first=new ItemFeatureRenderer.Submit(pose,ItemDisplayContext.GUI,0,0,0,tint,quads,ItemStackRenderState.FoilType.NONE);
                current.set(null,b);
                var second=new ItemFeatureRenderer.Submit(pose,ItemDisplayContext.GUI,0,0,0,tint,quads,ItemStackRenderState.FoilType.NONE);
                // This record is used only to exercise constructor metadata, never rendered.
                var renderType=net.minecraft.client.renderer.rendertype.RenderTypes.entitySolid(naturality.client.AtlasLocations.BLOCKS);
                var emptyModel=new net.minecraft.client.model.Model.Simple(
                    new net.minecraft.client.model.geom.ModelPart(java.util.List.of(),java.util.Map.of()), _ -> renderType);
                var model=new net.minecraft.client.renderer.feature.ModelFeatureRenderer.Submit<>(renderType,pose,emptyModel,net.minecraft.util.Unit.INSTANCE,0,0,0,null,null);
                check(PortalClippedCollector.plane(model)==b,"Model constructor did not capture its clipping plane");
                current.set(null,null);
                var ordinary=new ItemFeatureRenderer.Submit(pose,ItemDisplayContext.GUI,0,0,0,tint,quads,ItemStackRenderState.FoilType.NONE);
                check(first.equals(second) && first.equals(ordinary),"Fixture must have equal structural record values");
                for(int i=0;i<10000;i++) {
                    check(PortalClippedCollector.plane(first)==a,"First submission lost its plane");
                    check(PortalClippedCollector.plane(second)==b,"Equal submission borrowed another plane");
                    check(PortalClippedCollector.plane(ordinary)==null,"Unclipped submission inherited clipping");
                }
            } finally {current.set(null,previous);}
        } catch(ReflectiveOperationException e) {throw new AssertionError(e);}
    }

    private static void compareRays(Level level) {
        Random random=new Random(739502);
        for(var kind:new ClipContext.Block[]{ClipContext.Block.COLLIDER,ClipContext.Block.OUTLINE,ClipContext.Block.VISUAL}) {
            for(int i=0;i<256;i++) {
                Vec3 from=new Vec3(random.nextDouble()*38-19,120+random.nextDouble()*7,random.nextDouble()*38-19);
                Vec3 to=from.add(random.nextDouble()*16-8,random.nextDouble()*8-4,random.nextDouble()*16-8);
                var ray=new ClipContext(from,to,kind,ClipContext.Fluid.ANY,CollisionContext.empty());
                var expected=originalRay(level,ray);var actual=level.clip(ray);
                check(expected.getType()==actual.getType() && expected.getBlockPos().equals(actual.getBlockPos())
                    && expected.getDirection()==actual.getDirection() && expected.getLocation().distanceToSqr(actual.getLocation())<1e-16,
                    "Ray result changed: "+kind+" "+from+" -> "+to+" expected="+expected+" actual="+actual);
            }
        }
    }

    /** Vanilla traversal, with the original neighbor hooks but without the new palette scope. */
    private static BlockHitResult originalRay(Level level,ClipContext c) {
        var fire=FireGeometry.PICK_CONTEXT.get();var snow=SnowGeometry.PICK.get();var wind=WindShapes.PICK.get();
        try {
            if(GameplaySettings.fireWrapping(level))FireGeometry.PICK_CONTEXT.set(c);
            if(GameplaySettings.snowWrapping(level))SnowGeometry.PICK.set(c);
            if(WindShapes.active(level))WindShapes.PICK.set(c);
            return BlockGetter.traverseBlocks(c.getFrom(),c.getTo(),c,(context,pos) -> {
                var state=level.getBlockState(pos);var fluid=level.getFluidState(pos);
                var shape=context.getBlockShape(state,level,pos);
                var hit=level.clipWithInteractionOverride(context.getFrom(),context.getTo(),pos,shape,state);
                var water=context.getFluidShape(fluid,level,pos).clip(context.getFrom(),context.getTo(),pos);
                double d=hit==null?Double.MAX_VALUE:context.getFrom().distanceToSqr(hit.getLocation());
                double w=water==null?Double.MAX_VALUE:context.getFrom().distanceToSqr(water.getLocation());
                return d<=w?hit:water;
            },context -> {
                var delta=context.getFrom().subtract(context.getTo());
                return BlockHitResult.miss(context.getTo(),Direction.getApproximateNearest(delta.x,delta.y,delta.z),BlockPos.containing(context.getTo()));
            });
        } finally {
            if(fire==null)FireGeometry.PICK_CONTEXT.remove();else FireGeometry.PICK_CONTEXT.set(fire);
            if(snow==null)SnowGeometry.PICK.remove();else SnowGeometry.PICK.set(snow);
            if(wind==null)WindShapes.PICK.remove();else WindShapes.PICK.set(wind);
        }
    }

    private static void checkPresence(Level level) {
        var empty=new BlockPos(-1,175,-1);
        int all=RaycastShapePresence.FIRE|RaycastShapePresence.SNOW|RaycastShapePresence.WIND;
        var outer=RaycastShapePresence.begin(level);
        try {
            check(!RaycastShapePresence.mayContain(level,all,-1,175,-1,-1,175,-1),"Empty palette must bypass searches");
            var inner=RaycastShapePresence.begin(level);
            try {check(RaycastShapePresence.mayContain(level,RaycastShapePresence.WIND,-1,124,-1,0,124,0),"Leaves across negative section boundary missed");}
            finally {RaycastShapePresence.end(inner);}
            check(!RaycastShapePresence.mayContain(level,all,-1,175,-1,-1,175,-1),"Nested scope did not restore outer scope");
            check(RaycastShapePresence.mayContain(level,all,100000,120,100000,100000,120,100000),"Unavailable chunk must remain conservative");
        } finally {RaycastShapePresence.end(outer);}
        var old=level.getBlockState(empty);
        try {
            level.setBlock(empty,Blocks.SNOW.defaultBlockState(),2);
            var next=RaycastShapePresence.begin(level);
            try {check(RaycastShapePresence.mayContain(level,RaycastShapePresence.SNOW,-1,175,-1,-1,175,-1),"New ray missed newly added snow");}
            finally {RaycastShapePresence.end(next);}
        } finally {level.setBlock(empty,old,2);}
    }

    private static void checkShapes(Level level) {
        var pos=new BlockPos(0,124,0);var state=level.getBlockState(pos);
        var shape=Shapes.block();var thin=Shapes.box(0,0,0,1,.125,1);
        var offset=WindShapes.offset(level,pos,state);
        check(!offset.equals(Vec3.ZERO),"Wind fixture must actually translate leaves");
        var moved=WindShapes.move(level,pos,state,shape);
        check(moved.bounds().equals(shape.move(offset.x,0,offset.z).bounds()),"Translated shape changed");
        check(WindShapes.move(level,pos,state,shape)==moved,"Repeated shape query allocated a copy");
        var other=WindShapes.move(level,pos,state,thin);
        check(other.bounds().equals(thin.move(offset.x,0,offset.z).bounds()),"Collision context variant lost");
        check(WindShapes.raw(() -> WindShapes.move(level,pos,state,shape))==shape,"Raw geometry must remain unshifted");
        try {
            WindShapes.raw(() -> WindShapes.raw(() -> {throw new IllegalStateException("fixture");}));
            throw new AssertionError("Fixture did not throw");
        } catch(IllegalStateException expected) {
            check(WindShapes.move(level,pos,state,shape)==moved,"Nested exception left raw mode enabled");
        }
        check(WindShapes.move(level,pos,Blocks.STONE.defaultBlockState(),shape)==shape,"Ineligible state reused shifted bounds");
        for(int i=0;i<3;i++) {
            measure("shape-original",10000,() -> {
                var shift=WindShapes.offset(level,pos,state);
                sink=shape.move(shift.x,0,shift.z);
            });
            measure("shape-optimized",10000,() -> sink=WindShapes.move(level,pos,state,shape));
        }
    }

    private static void measure(String name,int count,Runnable operation) {
        var bean=(com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();
        long id=Thread.currentThread().threadId(),bytes=bean.getThreadAllocatedBytes(id),start=System.nanoTime();
        for(int i=0;i<count;i++)operation.run();
        double us=(System.nanoTime()-start)/1000.0/count;
        double allocation=(bean.getThreadAllocatedBytes(id)-bytes)/(double)count;
        System.out.printf(Locale.ROOT,"FOREST_BENCHMARK %s %.3f us/call %.1f bytes/call%n",name,us,allocation);
    }
}
