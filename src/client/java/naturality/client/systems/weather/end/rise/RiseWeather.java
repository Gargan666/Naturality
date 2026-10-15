package naturality.client.systems.weather.end.rise;

import java.util.ArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.Level;
import naturality.systems.weather.end.framework.EndWeatherSystem;
import naturality.client.systems.weather.wind.WindRendering.WindParticleControl;
import naturality.api.worlds.LoadedChunks;
import net.minecraft.core.BlockPos;
import net.minecraft.client.Camera;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.CollisionContext;

/** A local bounded particle volume rising out of open void below the islands. */
@org.jspecify.annotations.NullMarked
public final class RiseWeather {
    private static @org.jspecify.annotations.Nullable ClientLevel lastLevel;
    private static final ArrayList<RiseParticle> live=new ArrayList<>();
    private static double budget;
    private RiseWeather() {}
    public static void initialize() {
        RiseRenderLayer.initialize();
        naturality.client.systems.sleep.SleepVisuals.register(RiseWeather::tick);
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

    public static final class RiseParticle extends SingleQuadParticle {
        private final double vx,vy,vz;
        private final float colorPhase,brightnessPhase;
        public RiseParticle(ClientLevel level,double x,double y,double z) {
            super(level,x,y,z,null);
            vy=.35+random.nextDouble()*.4;vx=(random.nextDouble()-.5)*.1;vz=(random.nextDouble()-.5)*.1;
            colorPhase=random.nextFloat();brightnessPhase=random.nextFloat()*6.2831853F;
            quadSize=.18F+random.nextFloat()*.12F;hasPhysics=false;lifetime=260+random.nextInt(81);
        }
        @Override public void tick() {
            xo=x;yo=y;zo=z;
            if(++age>=lifetime || y>level.getMaxY()+16){remove();return;}
            double speed=naturality.client.systems.sleep.SleepVisuals.speed();
            var from=new Vec3(x,y,z);var next=from.add(vx*speed,vy*speed,vz*speed);
            if(level.clip(new ClipContext(from,next,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,CollisionContext.empty())).getType()!=HitResult.Type.MISS){remove();return;}
            setPos(next.x,next.y,next.z);
        }
        public float colorCoordinate(float partial) {return colorPhase+(age+partial)/200F;}
        public float brightnessCoordinate(float partial) {return .5F+.5F*(float)Math.sin(brightnessPhase+(age+partial)*.055);}
        public double upwardSpeed() {return vy;}
        public float widthRatio() {return (float)(1/(1+vy*5));}
        @Override public void extract(QuadParticleRenderState output,Camera camera,float partial) {
            var pos=new Vec3(Mth.lerp(partial,xo,x),Mth.lerp(partial,yo,y),Mth.lerp(partial,zo,z));
            var rotation=naturality.client.api.geometry.ParticleGeometry.orientation(new Vec3(vx,vy,vz),camera.position().subtract(pos));
            var relative=pos.subtract(camera.position());
            float opacity=Math.min(1,(age+partial)/15F)*Math.min(1,(lifetime-age-partial)/20F);
            float halfV=.5F/widthRatio();float u=colorCoordinate(partial);
            output.add(getLayer(),(float)relative.x,(float)relative.y,(float)relative.z,
                rotation.x,rotation.y,rotation.z,rotation.w,quadSize,u,u,-halfV,halfV,
                ARGB.colorFromFloat(opacity,1,1,1),Math.round(brightnessCoordinate(partial)*32767));
        }
        @Override protected Layer getLayer() {return RiseRenderLayer.LAYER;}
    }
}
