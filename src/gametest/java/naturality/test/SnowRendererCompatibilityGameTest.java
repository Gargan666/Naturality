package naturality.test;

import naturality.client.weather.WindRendering;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;

/** Snapshot-only lighting must work without accessing a live light engine. */
public final class SnowRendererCompatibilityGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        try(var world=context.worldBuilder().create()) {
        context.runOnClient(client -> {
            for(int brightness : new int[]{0,15}) {
                var snapshot=(BlockAndTintGetter)java.lang.reflect.Proxy.newProxyInstance(
                    BlockAndTintGetter.class.getClassLoader(),new Class<?>[]{BlockAndTintGetter.class},
                    (proxy,method,args)-> {
                        if(method.getName().equals("getBrightness")) return brightness;
                        if(method.getName().equals("getBlockState")) return Blocks.AIR.defaultBlockState();
                        throw new AssertionError("Unexpected render snapshot query: "+method.getName());
                    });
                if(WindRendering.exposed(snapshot,BlockPos.ZERO)!=(brightness>0))
                    throw new AssertionError("Snow exposure must use snapshot skylight");
                int tag=WindRendering.vertexTag(snapshot,BlockPos.ZERO,Blocks.OAK_LEAVES.defaultBlockState(),0,0,0);
                if((tag!=255)!=(brightness>0)) throw new AssertionError("Leaf tags must respect snapshot skylight");
            }

            var roofed=(BlockAndTintGetter)java.lang.reflect.Proxy.newProxyInstance(
                BlockAndTintGetter.class.getClassLoader(),new Class<?>[]{BlockAndTintGetter.class},
                (proxy,method,args)-> {
                    if(method.getName().equals("getBrightness")) return 15;
                    if(method.getName().equals("getBlockState")) {
                        BlockPos pos=(BlockPos)args[0];
                        return pos.getY()==3 ? Blocks.OAK_PLANKS.defaultBlockState() : Blocks.AIR.defaultBlockState();
                    }
                    throw new AssertionError("Unexpected roofed snapshot query: "+method.getName());
                });
            if(!WindRendering.exposed(roofed,BlockPos.ZERO))
                throw new AssertionError("Window-lit interior should retain skylight for snow compatibility");
            if(WindRendering.windExposed(roofed,BlockPos.ZERO))
                throw new AssertionError("Foliage wind must remain sheltered below an opaque roof");
        });
            var server=world.getServer();
            server.runCommand("gamemode spectator @a");
            server.runCommand("gamerule minecraft:random_tick_speed 0");
            server.runCommand("weather clear");
            server.runCommand("time set 6000");
            server.runCommand("fill -18 100 -3 18 100 3 oak_leaves[persistent=true]");
            server.runCommand("fill -18 101 -3 18 101 3 snow[layers=1]");
            server.runCommand("setblock 0 101 0 snow[layers=8]");
            server.runCommand("setblock 0 102 0 snow[layers=1]");
            server.runCommand("tp @a 0 105 -10 0 25");
            world.getConnection().waitForClientboundPackets();
            world.getConnection().waitForChunksRender();
            context.waitTicks(20);
            context.takeScreenshot("snow-renderer-compatibility");
            server.runCommand("fill -18 101 -3 18 101 3 snow[layers=2]");
            world.getConnection().waitForClientboundPackets();
            world.getConnection().waitForChunksRender();
            context.waitTicks(10);
        }
    }
}
