package naturality.client.weather;

import naturality.client.particle.PortalMoteGeometry;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.CollisionContext;

public final class RiseParticle extends SingleQuadParticle {
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
        var from=new Vec3(x,y,z);var next=from.add(vx,vy,vz);
        if(level.clip(new ClipContext(from,next,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,CollisionContext.empty())).getType()!=HitResult.Type.MISS){remove();return;}
        setPos(next.x,next.y,next.z);
    }
    public float colorCoordinate(float partial) {return colorPhase+(age+partial)/200F;}
    public float brightnessCoordinate(float partial) {return .5F+.5F*(float)Math.sin(brightnessPhase+(age+partial)*.055);}
    public double upwardSpeed() {return vy;}
    public float widthRatio() {return (float)(1/(1+vy*5));}
    @Override public void extract(QuadParticleRenderState output,Camera camera,float partial) {
        var pos=new Vec3(Mth.lerp(partial,xo,x),Mth.lerp(partial,yo,y),Mth.lerp(partial,zo,z));
        var rotation=PortalMoteGeometry.orientation(new Vec3(vx,vy,vz),camera.position().subtract(pos));
        var relative=pos.subtract(camera.position());
        float opacity=Math.min(1,(age+partial)/15F)*Math.min(1,(lifetime-age-partial)/20F);
        float halfV=.5F/widthRatio();float u=colorCoordinate(partial);
        output.add(getLayer(),(float)relative.x,(float)relative.y,(float)relative.z,
            rotation.x,rotation.y,rotation.z,rotation.w,quadSize,u,u,-halfV,halfV,
            ARGB.colorFromFloat(opacity,1,1,1),Math.round(brightnessCoordinate(partial)*32767));
    }
    @Override protected Layer getLayer() {return RiseRenderLayer.LAYER;}
}