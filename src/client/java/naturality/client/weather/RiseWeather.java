package naturality.client.weather;

import java.util.ArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.Level;
import naturality.weather.EndWeatherSystem;
import naturality.client.particle.WindParticleControl;
import naturality.util.LoadedChunks;
import net.minecraft.core.BlockPos;

/** A local bounded particle volume rising out of open void below the islands. */
public final class RiseWeather {
    private static @org.jspecify.annotations.Nullable ClientLevel lastLevel;
    private static final ArrayList<RiseParticle> live=new ArrayList<>();
    private static double budget;
    private RiseWeather() {}
    public static void initialize() {
        RiseRenderLayer.initialize();
        naturality.client.sleep.SleepVisuals.register(RiseWeather::tick);
    }
    private static void tick(Minecraft client) {
        var level = client.level; var player = client.player;
        if(level!=lastLevel){live.clear();budget=0;lastLevel=level;}
        if(level==null || player==null || !level.dimension().equals(Level.END))return;
        live.removeIf(p->!p.isAlive());
        float strength=EndWeatherSystem.state(level).rise();
        if(strength<=0){budget=0;return;}
        budget+=.1+3.9*Math.pow(strength/100.0,2);
        var random=level.getRandom();
        while(budget>=1) {
            budget--;
            if(live.size()>=600)continue;
            double angle=random.nextDouble()*Math.PI*2,radius=Math.sqrt(random.nextDouble())*80;
            double x=player.getX()+Math.cos(angle)*radius,z=player.getZ()+Math.sin(angle)*radius;
            if(!LoadedChunks.has(level,BlockPos.containing(x,0,z)))continue;
            var particle=new RiseParticle(level,x,level.getMinY()-8-random.nextDouble()*8,z);
            WindParticleControl.mark(particle,true);client.particleEngine.add(particle);live.add(particle);
        }
    }
}