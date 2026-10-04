package naturality.test;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import naturality.config.NaturalityConfig;
import naturality.weather.*;
import net.minecraft.core.BlockPos;

/** Tests explicit wind independently of automatic-weather schedules. */
public final class WindRendererGameTest implements FabricClientGameTest {
    private static void checkSnapshotEdges(net.minecraft.client.Minecraft client) {
        // The same view object may supply different geometry on the next rebuild.
        boolean[] roof={true};
        var reused=(net.minecraft.client.renderer.block.BlockAndTintGetter)java.lang.reflect.Proxy.newProxyInstance(
            net.minecraft.client.renderer.block.BlockAndTintGetter.class.getClassLoader(),
            new Class<?>[]{net.minecraft.client.renderer.block.BlockAndTintGetter.class},(proxy,method,args)-> {
                if(method.getName().equals("getBrightness"))return 15;
                if(method.getName().equals("getBlockState"))return roof[0] && ((BlockPos)args[0]).getY()==3
                    ? net.minecraft.world.level.block.Blocks.STONE.defaultBlockState()
                    : net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
                throw new AssertionError("Unexpected snapshot query: "+method.getName());
            });
        var leaves=net.minecraft.world.level.block.Blocks.SPRUCE_LEAVES.defaultBlockState();
        for(boolean sheltered:new boolean[]{true,false,true}) {
            roof[0]=sheltered;
            int[] marked={0};
            var emitter=net.fabricmc.fabric.api.client.renderer.v1.Renderer.get().quadEmitter(q -> {
                for(int i=0;i<4;i++) {
                    int tag=q.color(i)>>>24;
                    if(tag>=160 && tag<=191)marked[0]++;
                }
            });
            client.getModelManager().getBlockStateModelSet().get(leaves).emitQuads(emitter,reused,BlockPos.ZERO,leaves,
                net.minecraft.util.RandomSource.create(0),direction -> false);
            if((marked[0]==0)!=sheltered)throw new AssertionError("Stale wind exposure after snapshot reuse");
        }
        if(!net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("sodium")) return;
        try {
            var sliceClass=Class.forName("net.caffeinemc.mods.sodium.client.world.LevelSlice");
            var cacheClass=Class.forName("net.caffeinemc.mods.sodium.client.world.cloned.ClonedChunkSectionCache");
            var cache=cacheClass.getConstructor(net.minecraft.world.level.Level.class).newInstance(client.level);
            var slice=(net.minecraft.client.renderer.block.BlockAndTintGetter)sliceClass
                .getConstructor(net.minecraft.client.multiplayer.ClientLevel.class).newInstance(client.level);
            var prepare=sliceClass.getMethod("prepare",net.minecraft.world.level.Level.class,
                net.minecraft.core.SectionPos.class,cacheClass);
            var copy=sliceClass.getMethod("copyData",Class.forName("net.caffeinemc.mods.sodium.client.world.cloned.ChunkRenderContext"));
            for(int x=14;x<=17;x++) for(int z=14;z<=17;z++) {
                var pos=new BlockPos(x,111,z);
                copy.invoke(slice,prepare.invoke(null,client.level,net.minecraft.core.SectionPos.of(pos),cache));
                if(slice.getBrightness(net.minecraft.world.level.LightLayer.SKY,pos.above(8))<=0)
                    throw new AssertionError("Sodium snapshot truncated wind exposure at "+pos);
                int[] marked={0,0};
                var emitter=net.fabricmc.fabric.api.client.renderer.v1.Renderer.get().quadEmitter(q -> {
                    for(int i=0;i<4;i++) {
                        int tag=q.color(i)>>>24;
                        if(tag>=160 && tag<=191)marked[0]++;
                        if(tag>=224 && tag<=231)marked[1]++;
                    }
                });
                var state=client.level.getBlockState(pos);
                client.getModelManager().getBlockStateModelSet().get(state).emitQuads(emitter,slice,pos,state,
                    net.minecraft.util.RandomSource.create(0),direction -> false);
                if(marked[0]==0)throw new AssertionError("Snowy section-edge leaf lacks wind: "+pos);
                if(marked[1]==0)throw new AssertionError("Snow overlay lacks interpolated leaf-wind tags: "+pos);
            }
        } catch(ReflectiveOperationException e) { throw new AssertionError("Cannot test Sodium snapshot",e); }
    }
    @Override public void runTest(ClientGameTestContext context) {
        boolean[] old={true,false};
        context.runOnClient(client -> {
            old[0]=NaturalityConfig.get().effects.foliageWind;
            old[1]=client.options.improvedTransparency().get();
            NaturalityConfig.get().effects.foliageWind=true;
        });
        try(var world=context.worldBuilder().create()) {
            var server=world.getServer();
            server.runOnServer(s -> {
                var profile=new WeatherProfile(true);
                profile.overrideWind=profile.overrideRain=profile.overrideTemperature=true;
                profile.wind=100; profile.rain=0; profile.temperature=0;
                WeatherWorldData.get(s).setProfile("minecraft:overworld",profile);
            });
            server.runCommand("gamemode spectator @a");
            server.runCommand("gamerule minecraft:random_tick_speed 0");
            server.runCommand("time set 6000");
            server.runCommand("fill -8 99 -4 8 99 8 grass_block");
            server.runCommand("fill -4 100 0 4 100 2 short_grass");
            server.runCommand("fill -4 103 4 4 103 5 oak_leaves[persistent=true]");
            server.runCommand("fill -4 104 4 4 104 5 snow");
            server.runCommand("setblock 8 99 0 farmland");
            server.runCommand("setblock 8 100 0 wheat[age=7]");
            // Straddle X/Z boundaries at the very top of a section, with snow.
            server.runCommand("fill 14 111 14 17 111 17 spruce_leaves[persistent=true]");
            server.runCommand("fill 14 112 14 17 112 17 snow");
            server.runCommand("setblock 5 99 3 water");
            server.runCommand("fill 6 100 3 6 103 3 sugar_cane");
            server.runCommand("fill -6 100 3 -6 103 3 stone");
            server.runCommand("fill -6 100 2 -6 103 2 vine[south=true]");
            server.runCommand("setblock -10 103 3 oak_leaves[persistent=true]");
            server.runCommand("fill -10 100 2 -10 103 2 vine[south=true]");
            server.runCommand("tp @a 0 102 -6 0 0");
            world.getConnection().waitForClientboundPackets();
            world.getConnection().waitForChunksRender();
            // A pre-existing calm state needs 200 server ticks to reach 100,
            // plus the next weather packet; do not spend the predicate timeout
            // on that intentional ramp (especially after another suite).
            context.waitTicks(220);
            context.waitFor(client -> WeatherSystem.state(client.level)!=null && WeatherSystem.state(client.level).wind()==100);
            context.runOnClient(client -> {
                checkSnapshotEdges(client);
                var leafVineTop=new BlockPos(-10,103,2);
                var leafVine=client.level.getBlockState(leafVineTop);
                if(naturality.client.weather.WindRendering.vertexTag(client.level,leafVineTop,leafVine,.5F,1,.9375F)!=232)
                    throw new AssertionError("Vine top must follow its moving leaf support");
                for(int h=0;h<3;h++) {
                    var hanging=leafVineTop.below(h);
                    int bottom=naturality.client.weather.WindRendering.vertexTag(client.level,hanging,leafVine,.5F,0,.9375F);
                    int nextTop=naturality.client.weather.WindRendering.vertexTag(client.level,hanging.below(),leafVine,.5F,1,.9375F);
                    if(bottom!=102 || nextTop!=bottom)
                        throw new AssertionError("Unsupported vine joins must remain connected without wall constraints");
                }
                var vineTop=new BlockPos(-6,103,2);
                var vine=client.level.getBlockState(vineTop);
                if(naturality.client.weather.WindRendering.vertexTag(client.level,vineTop,vine,.5F,1,.9375F)!=101)
                    throw new AssertionError("Attached vine top edge must stay pinned");
                int vineJoin=naturality.client.weather.WindRendering.vertexTag(client.level,vineTop,vine,.5F,0,.9375F);
                if(vineJoin==101 || vineJoin!=naturality.client.weather.WindRendering.vertexTag(client.level,vineTop.below(),vine,.5F,1,.9375F))
                    throw new AssertionError("Vines below the anchored edge must sway together at their join");
                var cane=net.minecraft.world.level.block.Blocks.SUGAR_CANE.defaultBlockState();
                var caneRoot=new BlockPos(6,100,3);
                for(int h=0;h<3;h++) {
                    int top=naturality.client.weather.WindRendering.vertexTag(client.level,caneRoot.above(h),cane,.5F,1,.5F);
                    int bottom=naturality.client.weather.WindRendering.vertexTag(client.level,caneRoot.above(h+1),cane,.5F,0,.5F);
                    if(top!=h+1 || top!=bottom)throw new AssertionError("Cane must bend progressively with connected joints");
                }
                var weather=WeatherSystem.state(client.level);
                if(weather==null || weather.wind()!=100) throw new AssertionError("Explicit strong wind must reach the renderer");
                for(var pos:new BlockPos[]{new BlockPos(-4,103,4),new BlockPos(-4,100,0),new BlockPos(6,102,3),new BlockPos(-6,102,2),new BlockPos(8,100,0)}) {
                    var state=client.level.getBlockState(pos);
                    int[] marked={0,0,0};
                    float[] cropHeights={Float.POSITIVE_INFINITY,Float.NEGATIVE_INFINITY};
                    var emitter=net.fabricmc.fabric.api.client.renderer.v1.Renderer.get().quadEmitter(quad -> {
                        for(int i=0;i<4;i++) {
                            int alpha=quad.color(i)>>>24;
                            if(alpha<=127 || alpha>=160 && alpha<=191) marked[0]++;
                            if(alpha>=224 && alpha<=231) marked[2]++;
                            if(state.is(net.minecraft.tags.BlockTags.LEAVES) && alpha>=160 && alpha<=191
                                    || state.getBlock() instanceof net.minecraft.world.level.block.CropBlock && alpha>=120 && alpha<=127)
                                marked[1]++;
                            if(state.getBlock() instanceof net.minecraft.world.level.block.CropBlock && alpha>=120 && alpha<=127) {
                                int anchor=alpha-120;
                                float baseY=(float)Math.floor(quad.y(i)+.002F)-((anchor>>2)-1)-.0625F;
                                if(Math.abs(baseY+.0625F)>1e-5
                                        || Math.floor(quad.x(i)+.002F)-(anchor&1)!=0
                                        || Math.floor(quad.z(i)+.002F)-((anchor>>1)&1)!=0)
                                    throw new AssertionError("Crop vertices must share the farmland root");
                                float height=quad.y(i)-baseY;
                                cropHeights[0]=Math.min(cropHeights[0],height);
                                cropHeights[1]=Math.max(cropHeights[1],height);
                            }
                        }
                    });
                    client.getModelManager().getBlockStateModelSet().get(state).emitQuads(emitter,client.level,pos,state,
                        net.minecraft.util.RandomSource.create(0),direction -> false);
                    if(marked[0]==0) throw new AssertionError("Missing renderer-independent wind tags for "+state+" at "+pos);
                    if(state.getBlock() instanceof net.minecraft.world.level.block.CropBlock
                            && (Math.abs(cropHeights[0])>1e-5 || cropHeights[1]<.5F))
                        throw new AssertionError("Crop roots must stay pinned while upper vertices can bend");
                    if((state.is(net.minecraft.tags.BlockTags.LEAVES) || state.getBlock() instanceof net.minecraft.world.level.block.CropBlock)
                            && marked[1]==0)
                        throw new AssertionError("Missing shared-leaf or crop-root wind tags for "+state+" at "+pos);
                    if(state.is(net.minecraft.tags.BlockTags.LEAVES) && client.level.getBlockState(pos.above()).is(net.minecraft.world.level.block.Blocks.SNOW)
                            && marked[2]==0)
                        throw new AssertionError("Snow-on-leaf overlay lacks interpolated leaf-wind tags at "+pos);
                }
            });
            for(boolean oit:new boolean[]{false,true}) {
                context.runOnClient(client -> client.options.improvedTransparency().set(oit));
                context.waitTicks(10);
                context.takeScreenshot("wind-enabled-a-"+oit);
                context.waitTicks(13);
                context.takeScreenshot("wind-enabled-b-"+oit);
                context.runOnClient(client -> NaturalityConfig.get().effects.foliageWind=false);
                context.waitTicks(3);
                context.takeScreenshot("wind-disabled-"+oit);
                context.runOnClient(client -> NaturalityConfig.get().effects.foliageWind=true);
            }
        } finally {
            context.runOnClient(client -> {
                NaturalityConfig.get().effects.foliageWind=old[0];
                client.options.improvedTransparency().set(old[1]);
            });
        }
    }
}
