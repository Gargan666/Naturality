package naturality.test;

import naturality.client.fluid.*;
import naturality.config.NaturalityConfig;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/** Actual stepped fluid geometry checks moving spray, splash roots and outward face winding. */
public final class LiquidSlopesGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        var saved=NaturalityConfig.get().liquids;
        boolean[] options=new boolean[2];
        context.runOnClient(c->{NaturalityConfig.get().liquids=new NaturalityConfig.Liquids();options[0]=c.options.improvedTransparency().get();options[1]=c.gui.hud.isHidden();if(!options[1])c.gui.hud.toggle();});
        try(var world=context.worldBuilder().create()) {
            var server=world.getServer();
            server.runCommand("gamemode spectator @a");server.runCommand("time set 6000");server.runCommand("tick freeze");
            server.runCommand("fill -2 98 -2 10 99 10 stone");
            for(boolean lava:new boolean[]{false,true}) {
                int z=lava?6:0;
                String fluid=lava?"lava":"water";
                server.runCommand("fill -1 100 "+z+" -1 105 "+(z+2)+" stone");
                for(int x=0;x<9;x++) {
                    int y=103-x/3,level=1+(x%3)*3;
                    server.runCommand("fill "+x+" 100 "+z+" "+x+" "+(y-1)+" "+(z+2)+" stone");
                    server.runCommand("fill "+x+" "+y+" "+z+" "+x+" "+y+" "+(z+2)+" "+fluid+"[level="+level+"]");
                }
                server.runCommand("summon pig 1.5 103.6 "+(z+1.5)+" {NoAI:1b,NoGravity:1b,Invulnerable:1b,Tags:[\"slope_"+fluid+"\"]}");
            }
            server.runCommand("tp @a 5 106 -5 20 20");
            world.getConnection().waitForClientboundPackets();context.waitTicks(20);
            context.runOnClient(c->{
                for(boolean lava:new boolean[]{false,true}) {
                    double z=lava?7.5:1.5;
                    var start=java.util.Objects.requireNonNull(LiquidSurfaceGeometry.near(c.level,.5,103.5,z,lava,false));
                    var path=new FallSurfacePath(lava);
                    Vec3 point=new Vec3(.5,start.height()+.025,z);
                    for(int tick=0;tick<(lava?90:24);tick++) {
                        var next=path.step(c.level,point.x,point.y,point.z,0,-.03,0);
                        check(next!=null,"Stream spray must remain on the stepped liquid path");
                        point=next;
                        var contact=LiquidSurfaceGeometry.near(c.level,point.x,point.y,point.z,lava,false);
                        check(contact!=null && point.y>=contact.height(),"Spray stays above liquid: lava="+lava+", tick="+tick+", position="+point+", surface="+contact);
                    }
                    check(point.x>3 && point.y<start.height()-.8,"Spray crosses a ledge: lava="+lava+", final="+point+", start="+start);
                    net.minecraft.world.entity.Entity entity=null;
                    for(var e:c.level.entitiesForRendering())if(e instanceof net.minecraft.world.entity.animal.pig.Pig && Math.abs(e.getZ()-z)<.5)entity=e;
                    entity=java.util.Objects.requireNonNull(entity);
                    var eye=new Vec3(20,120,30);
                    if(lava){LavaImpactColumn.spawn(c.level,entity,12);var output=new LavaIntersection();LavaImpactColumn.render(output,c.level,eye,3);checkMesh(output,entity,eye);}
                    else {WaterImpactColumn.spawn(c.level,entity,12);var output=new WaterIntersection();WaterImpactColumn.render(output,c.level,eye,1.5F);checkMesh(output,entity,eye);}
                    var insideEye=new Vec3(entity.getX(),entity.getY()+1,entity.getZ());
                    if(lava){var output=new LavaIntersection();LavaImpactColumn.render(output,c.level,insideEye,3);checkMesh(output,entity,insideEye);}
                    else {var output=new WaterIntersection();WaterImpactColumn.render(output,c.level,insideEye,1.5F);checkMesh(output,entity,insideEye);}
                }
            });
            server.runCommand("tick step 3");context.waitTicks(20);
            for(boolean improved:new boolean[]{false,true}) {
                server.runCommand("tp @a 5 106 -5 20 20");world.getConnection().waitForClientboundPackets();
                context.runOnClient(c->c.options.improvedTransparency().set(improved));context.waitTicks(10);
                world.getConnection().waitForChunksRender();context.takeScreenshot("liquid-slope-splashes-"+improved);
                server.runCommand("tp @a 8 106 9 125 20");world.getConnection().waitForClientboundPackets();context.waitTicks(10);
                context.takeScreenshot("liquid-slope-splashes-reverse-"+improved);
                server.runCommand("tp @a 1.85 103.3 1.95 0 5");world.getConnection().waitForClientboundPackets();context.waitTicks(10);
                context.takeScreenshot("liquid-slope-splash-inside-"+improved);
            }
        } finally {context.runOnClient(c->{NaturalityConfig.get().liquids=saved;c.options.improvedTransparency().set(options[0]);if(c.gui.hud.isHidden()!=options[1])c.gui.hud.toggle();WaterImpactColumn.clear();LavaImpactColumn.clear();});}
    }
    private static void checkMesh(net.minecraft.client.renderer.state.level.QuadParticleRenderState output,net.minecraft.world.entity.Entity entity,Vec3 eye) {
        try {
            var field=output.getClass().getDeclaredField("SPLASH_LAYER");field.setAccessible(true);
            var layer=(net.minecraft.client.particle.SingleQuadParticle.Layer)field.get(null);
            var vertices=new java.util.ArrayList<Vec3>();
            var consumer=(com.mojang.blaze3d.vertex.VertexConsumer)java.lang.reflect.Proxy.newProxyInstance(LiquidSlopesGameTest.class.getClassLoader(),new Class<?>[]{com.mojang.blaze3d.vertex.VertexConsumer.class},(proxy,method,args)->{
                if(method.getName().equals("addVertex") && args.length==3)vertices.add(new Vec3((float)args[0],(float)args[1],(float)args[2]).add(eye));
                return method.getReturnType()==void.class?null:proxy;
            });
            output.buildLayer(layer,consumer);
            check(!vertices.isEmpty(),"Slope splash has visible geometry");
            var bases=new java.util.HashMap<String,Double>();
            for(int i=0;i<vertices.size();i+=4) {
                var a=vertices.get(i);var b=vertices.get(i+1);var d=vertices.get(i+2);var t=vertices.get(i+3);
                var center=a.add(b).add(d).add(t).scale(.25);
                var normal=b.subtract(a).cross(d.subtract(a));
                check(normal.dot(center.subtract(new Vec3(entity.getX(),center.y,entity.getZ())))>0,"Double-sided splash planes keep consistent outward winding from every viewpoint");
                bases.merge(center.x+":"+center.z,Math.min(Math.min(a.y,b.y),Math.min(d.y,t.y)),Math::min);
            }
            double low=java.util.Collections.min(bases.values()),high=java.util.Collections.max(bases.values());
            check(high-low>.06,"Individual pixel columns follow different slope heights instead of sharing a flat base");
        } catch(ReflectiveOperationException e){throw new RuntimeException(e);}
    }
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
