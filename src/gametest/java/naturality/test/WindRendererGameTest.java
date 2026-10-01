package naturality.test;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import naturality.config.NaturalityConfig;
import naturality.weather.*;
import net.minecraft.core.BlockPos;

/** Tests explicit wind independently of automatic-weather schedules. */
public final class WindRendererGameTest implements FabricClientGameTest {
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
            server.runCommand("setblock 5 99 3 water");
            server.runCommand("fill 6 100 3 6 103 3 sugar_cane");
            server.runCommand("fill -6 100 3 -6 103 3 stone");
            server.runCommand("fill -6 100 2 -6 103 2 vine[south=true]");
            server.runCommand("tp @a 0 102 -6 0 0");
            world.getConnection().waitForClientboundPackets();
            world.getConnection().waitForChunksRender();
            context.waitFor(client -> WeatherSystem.state(client.level)!=null && WeatherSystem.state(client.level).wind()==100);
            context.runOnClient(client -> {
                var weather=WeatherSystem.state(client.level);
                if(weather==null || weather.wind()!=100) throw new AssertionError("Explicit strong wind must reach the renderer");
                for(var pos:new BlockPos[]{new BlockPos(-4,103,4),new BlockPos(-4,100,0),new BlockPos(6,102,3),new BlockPos(-6,102,2)}) {
                    var state=client.level.getBlockState(pos);
                    int[] marked={0};
                    var emitter=net.fabricmc.fabric.api.client.renderer.v1.Renderer.get().quadEmitter(quad -> {
                        for(int i=0;i<4;i++) {
                            int alpha=quad.color(i)>>>24;
                            if(alpha>=64 && alpha<=117 || alpha>=160 && alpha<=191) marked[0]++;
                        }
                    });
                    client.getModelManager().getBlockStateModelSet().get(state).emitQuads(emitter,client.level,pos,state,
                        net.minecraft.util.RandomSource.create(0),direction -> false);
                    if(marked[0]==0) throw new AssertionError("Missing renderer-independent wind tags for "+state+" at "+pos);
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
