package naturality.test;

import naturality.weather.*;
import naturality.client.weather.*;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;

public final class RiseGameTest implements FabricClientGameTest {
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    @Override public void runTest(ClientGameTestContext context) {
        var natural=new EndWeatherCycle(71);
        for(int i=0;i<150000;i++){natural.tick(true);check(natural.state().starfall()==0,"Natural starfall stays disabled");}
        natural.set("starfall",100);for(int i=0;i<200;i++)natural.tick(false);
        check(natural.state().starfall()==100,"Manual starfall remains available");
        natural.set("starfall",-1);for(int i=0;i<200;i++)natural.tick(false);
        check(natural.state().starfall()==0,"Auto returns starfall to calm even while weather is paused");
        var cycle=new EndWeatherCycle(37);cycle.set("rise",100);for(int i=0;i<200;i++)cycle.tick(false);
        check(cycle.state().rise()==100,"Rise override eases to full strength");
        var copy=new EndWeatherCycle(1);copy.restore(cycle.snapshot());
        for(int i=0;i<30000;i++){cycle.tick(true);copy.tick(true);check(cycle.snapshot().equals(copy.snapshot()),"Rise state and clocks survive save/load");}
        try(var world=context.worldBuilder().create()) {
            var server=world.getServer();
            server.runCommand("gamemode spectator @a");
            server.runCommand("execute in minecraft:the_end run tp @a 0 30 -12 0 -15");
            server.runCommand("gamerule minecraft:advance_weather false");
            context.waitTicks(40);
            server.runCommand("execute in minecraft:the_end run fill -12 20 -15 12 55 12 air");
            server.runCommand("weather minecraft:the_end rise 100");
            context.waitTicks(205);
            server.runOnServer(s->check(EndWeatherSystem.state(s.getLevel(Level.END)).rise()==100,"Rise command addresses the End pool"));
            context.runOnClient(c->{
                check(EndWeatherSystem.state(c.level).rise()==100,"Rise strength is synchronized to the client");
                try {
                    var field=RiseWeather.class.getDeclaredField("live");field.setAccessible(true);
                    var particles=(java.util.List<?>)field.get(null);
                    check(particles.size()>50 && particles.size()<=600,"Weather creates a bounded rising particle population");
                    var p=(RiseParticle)particles.getFirst();
                    check(p.upwardSpeed()>0 && p.widthRatio()<.5,"Particles rise with a velocity-aligned elongated footprint");
                    check(p.colorCoordinate(20)!=p.colorCoordinate(0),"Hue traverses palette columns");
                    check(Math.abs(p.brightnessCoordinate(20)-p.brightnessCoordinate(0))>.0001,"Brightness pulses independently through palette rows");
                    check(naturality.client.particle.WindParticleControl.immune(p),"Weather motion is independent of ordinary particle wind");
                }catch(ReflectiveOperationException e){throw new AssertionError(e);}
                var velocity=new Vec3(.05,.5,-.03);
                var orientation=naturality.client.particle.PortalMoteGeometry.orientation(velocity,new Vec3(2,3,5));
                var axis=orientation.transform(new org.joml.Vector3f(1,0,0));
                check(new Vec3(axis.x,axis.y,axis.z).dot(velocity.normalize())>.999,"Long edge is locked to velocity");
                var pos=new BlockPos(4,25,4);c.level.setBlock(pos,Blocks.END_STONE.defaultBlockState(),3);
                var blocked=new RiseParticle(c.level,4.5,24.99,4.5);blocked.tick();
                check(!blocked.isAlive(),"Rising particles stop at island terrain");
                for(int x=-4;x<=4;x++)for(int y=0;y<4;y++) {
                    var visual=new RiseParticle(c.level,x,30+y*2,0);
                    naturality.client.particle.WindParticleControl.mark(visual,true);c.particleEngine.add(visual);
                }
            });
            context.waitTicks(20);context.takeScreenshot("rise-aurora-colored-rectangles");
            server.runCommand("weather minecraft:the_end rise 0");context.waitTicks(205);
            context.runOnClient(c->check(EndWeatherSystem.state(c.level).rise()==0,"Rise can be disabled by command"));
        }
    }
}