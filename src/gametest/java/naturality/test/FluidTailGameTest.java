package naturality.test;

import naturality.client.fluid.FallingFluidTail;
import naturality.config.NaturalityConfig;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.core.BlockPos;

/** Suspended falling columns exercise cap removal, section-boundary geometry and both transparency paths. */
public final class FluidTailGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        var previous=NaturalityConfig.get().liquids;
        boolean[] saved=new boolean[2];
        context.runOnClient(c->{
            NaturalityConfig.get().liquids=new NaturalityConfig.Liquids();
            saved[0]=c.options.improvedTransparency().get();saved[1]=c.gui.hud.isHidden();
            if(!saved[1])c.gui.hud.toggle();
        });
        try(var world=context.worldBuilder().create()) {
            var server=world.getServer();
            server.runCommand("gamemode spectator @a");
            server.runCommand("time set 6000");
            server.runCommand("tick freeze");
            server.runCommand("fill -8 104 -8 8 104 8 stone");
            server.runCommand("fill -2 112 0 -2 114 0 water[level=8]");
            server.runCommand("setblock -2 115 0 water");
            server.runCommand("fill 2 112 0 2 114 0 lava[level=8]");
            server.runCommand("setblock 2 115 0 lava");
            server.runCommand("setblock 5 112 0 water[level=8]");
            server.runCommand("setblock 5 111 0 stone");
            server.runCommand("tp @a 0 112 -6 0 0");
            world.getConnection().waitForClientboundPackets();
            context.waitTicks(20);
            context.runOnClient(c->{
                for(int x:new int[]{-2,2}) {
                    var pos=new BlockPos(x,112,0);
                    check(FallingFluidTail.eligible(c.level,pos,c.level.getFluidState(pos)),"Air-ended falling column gets a tail");
                    check(c.level.getBlockState(pos.below()).isAir(),"Tail remains purely visual air");
                    check(!FallingFluidTail.eligible(c.level,pos.above(),c.level.getFluidState(pos.above())),"No tail between fluid blocks");
                    check(!FallingFluidTail.eligible(c.level,pos.above(3),c.level.getFluidState(pos.above(3))),"Resting sources have no tail");
                    checkMesh(c,pos);
                    checkMesh(c,pos.above(3),false);
                }
                var grounded=new BlockPos(5,112,0);
                check(!FallingFluidTail.eligible(c.level,grounded,c.level.getFluidState(grounded)),"Tail does not extend through ground");
            });
            for(boolean improved:new boolean[]{false,true}) {
                context.runOnClient(c->c.options.improvedTransparency().set(improved));
                server.runCommand("tp @a 0 112 -6 0 0");
                world.getConnection().waitForClientboundPackets();
                context.waitTicks(10);
                world.getConnection().waitForChunksRender();
                context.takeScreenshot("falling-fluid-tail-"+improved);
                server.runCommand("tp @a 0 109 -4 0 -25");
                world.getConnection().waitForClientboundPackets();
                context.waitTicks(10);
                context.takeScreenshot("falling-fluid-underside-"+improved);
            }
        } finally {
            context.runOnClient(c->{
                NaturalityConfig.get().liquids=previous;
                c.options.improvedTransparency().set(saved[0]);
                if(c.gui.hud.isHidden()!=saved[1])c.gui.hud.toggle();
            });
        }
    }
    private static void checkMesh(net.minecraft.client.Minecraft client,BlockPos pos) {
        checkMesh(client,pos,true);
    }
    private static void checkMesh(net.minecraft.client.Minecraft client,BlockPos pos,boolean hasTail) {
        var vertices=new java.util.ArrayList<float[]>();
        var consumer=(com.mojang.blaze3d.vertex.VertexConsumer)java.lang.reflect.Proxy.newProxyInstance(
            FluidTailGameTest.class.getClassLoader(),new Class<?>[]{com.mojang.blaze3d.vertex.VertexConsumer.class},
            (proxy,method,args)->{
                if(method.getName().equals("addVertex") && args.length==11)
                    vertices.add(new float[]{(float)args[0],(float)args[1],(float)args[2],
                        (((int)args[7]&FallingFluidTail.LIGHT_FLAG)!=0)?1:0,
                        (int)args[7]&~FallingFluidTail.LIGHT_FLAG,
                        (int)args[3]&0xFFFFFF});
                return method.getReturnType()==void.class?null:proxy;
            });
        var renderer=new net.minecraft.client.renderer.block.FluidRenderer(client.getModelManager().getFluidStateModelSet());
        var block=client.level.getBlockState(pos);
        renderer.tesselate(client.level,pos,layer->consumer,block,block.getFluidState());
        int tails=0,actualSides=0,recessedCaps=0;
        for(int i=0;i<vertices.size();i+=4) {
            var first=vertices.get(i);
            boolean horizontal=true;
            for(int j=0;j<4;j++)horizontal&=Math.abs(vertices.get(i+j)[1]-first[1])<0.00001F;
            if(first[3]==1) {tails++;check(!horizontal,"Visual tail must have no horizontal cap");}
            else {
                if(!horizontal)actualSides++;
                else if(Math.abs(first[1]-((pos.getY()&15)+FallingFluidTail.UNDERSIDE_OFFSET-0.001F))<0.00001F)recessedCaps++;
                check(!horizontal || Math.abs(first[1]-(pos.getY()&15))>0.01F,"Actual falling column bottom cap must be removed");
            }
        }
        check(tails==(hasTail?8:0),"Tail includes both normal and reversed side quads; got "+tails);
        check(actualSides==8,"Actual falling sides retain normal and reversed quads; got "+actualSides);
        check(recessedCaps==(hasTail?1:0),"Only air-ended falling fluid has its original underside recessed upward");
        for(var tail:vertices)if(tail[3]==1 && tail[1]==(pos.getY()&15)) {
            boolean matched=false;
            for(var actual:vertices)if(actual[3]==0 && actual[0]==tail[0] && actual[1]==tail[1] && actual[2]==tail[2]) {
                check(actual[4]==tail[4],"Join vertices share identical light samples");
                check(actual[5]==tail[5],"Join vertices share identical biome tint and face shading");
                matched=true;
            }
            check(matched,"Tail joins the exact endpoint of an actual fluid side");
        }
    }
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
