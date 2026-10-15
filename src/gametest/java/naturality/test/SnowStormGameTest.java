package naturality.test;

import java.lang.management.ManagementFactory;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import naturality.snow.SnowfallBudget;

/** Dense, changing snow rather than a static snow-mesh benchmark. */
public final class SnowStormGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        try(var world=context.worldBuilder().create()) {
            var server=world.getServer();
            server.runCommand("gamemode spectator @a");
            server.runCommand("tp @a 8 112 8 0 65");
            server.runCommand("gamerule minecraft:random_tick_speed 0");
            server.runCommand("fill -16 100 -16 31 100 31 grass_block");
            server.runCommand("fill 0 101 0 15 101 15 oak_leaves[persistent=true]");
            server.runCommand("fill 16 101 0 31 101 15 oak_fence");
            server.runCommand("fill 0 102 0 31 102 15 snow[layers=2]");
            world.getConnection().waitForClientboundPackets();
            world.getConnection().waitForChunksRender();
            for(int round=0;round<3;round++) {
                final int layers=round+3;
                server.runOnServer(s -> {
                    var bean=(com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();
                    long id=Thread.currentThread().threadId(),bytes=bean.getThreadAllocatedBytes(id),start=System.nanoTime();
                    for(int x=0;x<32;x++)for(int z=0;z<16;z++)
                        s.overworld().setBlockAndUpdate(new BlockPos(x,102,z),Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS,layers));
                    System.out.println("SNOW_STORM_UPDATE layers="+layers+" elapsed_ms="+(System.nanoTime()-start)/1e6
                        +" bytes="+(bean.getThreadAllocatedBytes(id)-bytes));
                });
                world.getConnection().waitForClientboundPackets();context.waitTicks(5);
            }
            context.takeScreenshot("snow-storm-layers");
            // A fixed-order stream simulates a large storm without relying on
            // random chunk traversal. Each simulated expensive preflight uses
            // the soft time budget; the first admitted write must still finish.
            var visited=new java.util.HashSet<Integer>();
            for(int tick=0;tick<40;tick++) {
                context.waitTicks(1);
                server.runOnServer(s -> {
                    var level=s.overworld();int changes=0;
                    for(int i=0;i<16;i++) {
                        var pos=new BlockPos(i,102,0);
                        if(!SnowfallBudget.tryAttempt(level,pos))continue;
                        SnowfallBudget.recordWork(level,System.nanoTime()-SnowfallBudget.MAX_WORK_NANOS-1);
                        var old=level.getBlockState(pos);
                        var next=old.setValue(SnowLayerBlock.LAYERS,old.getValue(SnowLayerBlock.LAYERS)==5?6:5);
                        if(SnowfallBudget.grow(level,pos,next)) {changes++;visited.add(i);}
                    }
                    if(changes>1)throw new AssertionError("Soft-budget overrun admitted more than one snow update");
                });
            }
            if(visited.size()!=16)throw new AssertionError("Later storm columns starved: "+visited);
            server.runOnServer(s -> {
                var config=naturality.config.NaturalityServerConfig.get();
                boolean wrapping=config.snowWrapping,accumulation=config.weatherSnowAccumulation;
                try {
                    config.snowWrapping=false;config.weatherSnowAccumulation=false;
                    for(int i=0;i<100;i++)if(!SnowfallBudget.tryAttempt(s.overworld(),new BlockPos(i,102,0)))
                        throw new AssertionError("Disabled snow changes still throttled vanilla snowfall");
                } finally {config.snowWrapping=wrapping;config.weatherSnowAccumulation=accumulation;}
            });
            context.waitTicks(1);
            server.runOnServer(s -> {
                int accepted=0;
                for(int i=0;i<1000;i++)if(SnowfallBudget.tryAttempt(s.overworld(),new BlockPos(0,102,0)))accepted++;
                if(accepted>SnowfallBudget.MAX_ATTEMPTS)throw new AssertionError("Preflight cap exceeded: "+accepted);
            });
            context.waitTicks(1);
            server.runOnServer(s -> {
                int changes=0;
                for(int i=0;i<64;i++) {
                    var pos=new BlockPos(i%32,102,1+i/32);var old=s.overworld().getBlockState(pos);
                    if(SnowfallBudget.grow(s.overworld(),pos,old.setValue(SnowLayerBlock.LAYERS,6)))changes++;
                }
                if(changes==0 || changes>SnowfallBudget.MAX_CHANGES)throw new AssertionError("Snow write cap/progress failed: "+changes);
            });
            server.runOnServer(s -> {
                for(int i=0;i<12;i++) {
                    var pos=new BlockPos((i%4-1)*16,131,(i/4-1)*16);
                    s.overworld().setBlockAndUpdate(pos.below(),Blocks.STONE.defaultBlockState());
                    s.overworld().setBlockAndUpdate(pos,Blocks.SNOW.defaultBlockState());
                }
            });
            context.waitTicks(1);
            server.runOnServer(s -> {
                int sections=0;
                for(int i=0;i<12;i++) {
                    var pos=new BlockPos((i%4-1)*16,131,(i/4-1)*16);
                    if(SnowfallBudget.grow(s.overworld(),pos,Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS,2)))sections++;
                }
                if(sections==0 || sections>SnowfallBudget.MAX_SECTIONS)throw new AssertionError("Snow section cap/progress failed: "+sections);
            });
            // Preserve vertical support invalidation after bypassing horizontal
            // partial-snow support checks.
            server.runOnServer(s -> {
                var pos=new BlockPos(-8,102,-8);
                s.overworld().setBlockAndUpdate(pos.below(),Blocks.STONE.defaultBlockState());
                s.overworld().setBlockAndUpdate(pos,Blocks.SNOW.defaultBlockState());
                s.overworld().setBlockAndUpdate(pos.below(),Blocks.AIR.defaultBlockState());
                if(s.overworld().getBlockState(pos).is(Blocks.SNOW))throw new AssertionError("Unsupported snow survived removal of its floor");
            });
            System.out.println("SNOW_STORM_BUDGET: bounded preflights, soft-limit progress, all 16 fixed-order columns and support removal passed");
            var profile=new naturality.weather.WeatherProfile(true);
            profile.overrideRain=profile.overrideTemperature=profile.overrideWind=true;
            profile.rain=100;profile.temperature=0;profile.wind=0;
            server.runOnServer(s -> naturality.weather.WeatherWorldData.get(s).setProfile("minecraft:overworld",profile));
            context.waitTicks(410);
            server.runOnServer(s -> {
                for(int x=0;x<32;x++)for(int z=0;z<16;z++)s.overworld().setBlock(new BlockPos(x,102,z),
                    Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS,2),18);
            });
            int[] total={0};
            for(int tick=0;tick<32;tick++) {
                context.waitTicks(1);
                server.runOnServer(s -> {
                    var level=s.overworld();var before=new net.minecraft.world.level.block.state.BlockState[512];
                    for(int x=0;x<32;x++)for(int z=0;z<16;z++)before[x*16+z]=level.getBlockState(new BlockPos(x,102,z));
                    for(int x=0;x<32;x++)for(int z=0;z<16;z++)level.tickPrecipitation(new BlockPos(x,102,z));
                    int changed=0;
                    for(int x=0;x<32;x++)for(int z=0;z<16;z++)if(level.getBlockState(new BlockPos(x,102,z))!=before[x*16+z])changed++;
                    if(changed>SnowfallBudget.MAX_CHANGES)throw new AssertionError("Natural snowfall bypassed the write cap: "+changed);
                    total[0]+=changed;
                });
            }
            if(total[0]==0)throw new AssertionError("Heavy snow stopped accumulating");
            world.getConnection().waitForClientboundPackets();
            var saved=TestFixtures.require(server.computeOnServer(s -> {
                int[] layers=new int[512];
                for(int x=0;x<32;x++)for(int z=0;z<16;z++)layers[x*16+z]=s.overworld().getBlockState(new BlockPos(x,102,z)).getValue(SnowLayerBlock.LAYERS);
                return layers;
            }), "saved");
            context.runOnClient(c -> {
                for(int x=0;x<32;x++)for(int z=0;z<16;z++) {
                    int layers=TestFixtures.level(c).getBlockState(new BlockPos(x,102,z)).getValue(SnowLayerBlock.LAYERS);
                    if(layers!=saved[x*16+z])throw new AssertionError("Client snow height is stale");
                }
            });
            System.out.println("SNOW_STORM_WEATHER: "+total[0]+" layer changes across 32 storm ticks, client heights synchronized");
        }
    }
}
