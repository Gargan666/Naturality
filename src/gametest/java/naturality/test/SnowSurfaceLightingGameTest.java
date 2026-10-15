package naturality.test;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.renderer.v1.Renderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;

/** Nighttime lantern beside ordinary and fitted snow in the same ground plane. */
public final class SnowSurfaceLightingGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        try (var world = context.worldBuilder().create()) {
            var server = world.getServer();
            server.runCommand("gamemode spectator @a");
            server.runCommand("time set midnight");
            server.runCommand("gamerule minecraft:random_tick_speed 0");
            server.runCommand("fill -5 100 -5 5 100 5 grass_block");
            server.runCommand("fill -5 101 -5 5 101 5 snow");
            server.runCommand("setblock 0 101 0 soul_lantern");
            server.runCommand("setblock 0 102 0 snow");
            server.runCommand("tp @a 2 102 -3 35 50");
            world.getConnection().waitForClientboundPackets(); context.waitTicks(30);
            context.runOnClient(c -> {
                var ordinary = new java.util.HashMap<String,Integer>();
                var fitted = new java.util.HashMap<String,Integer>();
                var lighter = new net.fabricmc.fabric.impl.client.indigo.renderer.render.AltModelBlockRendererImpl(true,true,c.getBlockColors());
                for (var sample : new BlockPos[]{new BlockPos(1,101,0),new BlockPos(-1,101,0),
                        new BlockPos(0,101,1),new BlockPos(0,101,-1),new BlockPos(0,102,0)}) {
                    var state = TestFixtures.level(c).getBlockState(sample);
                    var vertices = sample.getY()==102 ? fitted : ordinary;
                    var sink = Renderer.get().quadEmitter(q -> {
                        if (q.lightFace()!=Direction.UP) return;
                        for (int i=0;i<4;i++) {
                            if (Math.abs(q.y(i)-101.125F)>.0001F) continue;
                            String key=Math.round(q.x(i)*1024)+":"+Math.round(q.z(i)*1024);
                            vertices.put(key,q.lightmap(i));
                        }
                    });
                    lighter.tesselateBlock(sink,sample.getX(),sample.getY(),sample.getZ(),TestFixtures.level(c),sample,state,
                        c.getModelManager().getBlockStateModelSet().get(state),0);
                }
                int shared=0;
                for(var entry:fitted.entrySet()) if(ordinary.containsKey(entry.getKey())) {
                    shared++;
                    if(!entry.getValue().equals(ordinary.get(entry.getKey())))
                        throw new AssertionError("Snow light seam at shared vertex "+entry);
                }
                if(shared<4)throw new AssertionError("Fixture must compare at least four shared snow corners");
                for (var pos : new BlockPos[]{new BlockPos(1,101,0),new BlockPos(0,102,0)}) {
                    var modes = new java.util.HashSet<String>();
                    var state = TestFixtures.level(c).getBlockState(pos);
                    c.getModelManager().getBlockStateModelSet().get(state).emitQuads(Renderer.get().quadEmitter(q -> {
                        if (q.lightFace() == Direction.UP) {
                            modes.add(q.shadeMode() + " AO=" + q.ambientOcclusion());
                            if (q.shadeMode() != net.fabricmc.fabric.api.client.renderer.v1.mesh.ShadeMode.VANILLA)
                                throw new AssertionError("Fitted snow must share the ordinary snow lighting stencil");
                        }
                    }),TestFixtures.level(c),pos,state,RandomSource.create(0),_ -> false);
                    System.out.println("Snow lighting modes at " + pos + ": " + modes);
                }
            });
            world.getConnection().waitForChunksRender();
            context.takeScreenshot("snow-lantern-lighting-fixed");
        }
    }
}
