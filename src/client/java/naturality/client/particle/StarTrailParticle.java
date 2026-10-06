package naturality.client.particle;

import java.util.WeakHashMap;
import naturality.NaturalityParticles;
import naturality.starfall.FallingStar;
import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Quaternionf;

/** One card attached to the star center. Its Y axis trails velocity and only its roll faces the observer. */
public final class StarTrailParticle extends SingleQuadParticle {
    private static SpriteSet sprites;
    private static ClientLevel lastLevel;
    private static final WeakHashMap<FallingStar,StarTrailParticle> attached=new WeakHashMap<>();
    private final FallingStar star;
    private final Matrix3f basis=new Matrix3f();
    private final Quaternionf rotation=new Quaternionf();
    private Vec3 velocity;
    private long lastTick;
    public static void initialize() {
        ParticleProviderRegistry.getInstance().register(NaturalityParticles.STAR_TRAIL,set->{
            sprites=set;
            return (_,level,x,y,z,vx,vy,vz,_)->new StarTrailParticle(level,x,y,z,new Vec3(vx,vy,vz),set.first(),null);
        });
        ClientTickEvents.END_CLIENT_TICK.register(client->{
            if(client.level!=lastLevel) {attached.clear();lastLevel=client.level;}
            if(client.level==null || sprites==null)return;
            attached.entrySet().removeIf(entry->entry.getKey().isRemoved());
            for(var entity:client.level.entitiesForRendering())if(entity instanceof FallingStar star) {
                var old=attached.get(star);
                if(old!=null && old.isAlive() && client.level.getGameTime()-old.lastTick<=2)continue;
                var p=new StarTrailParticle(client.level,star.getX(),star.getY()+star.getBbHeight()/2,star.getZ(),star.getDeltaMovement(),sprites.first(),star);
                WindParticleControl.mark(p,true);client.particleEngine.add(p);attached.put(star,p);
            }
        });
    }
    private StarTrailParticle(ClientLevel level,double x,double y,double z,Vec3 velocity,TextureAtlasSprite sprite,FallingStar star) {
        super(level,x,y,z,sprite);this.star=star;this.velocity=velocity;hasPhysics=false;quadSize=3;lifetime=25;
        lastTick=level.getGameTime();
    }
    @Override public void tick() {
        lastTick=level.getGameTime();xo=x;yo=y;zo=z;
        if(star!=null) {
            if(star.isRemoved()) {remove();return;}
            setPos(star.getX(),star.getY()+star.getBbHeight()/2,star.getZ());velocity=star.getDeltaMovement();
        } else {if(++age>=lifetime){remove();return;}setPos(x+velocity.x,y+velocity.y,z+velocity.z);}
    }
    @Override public void extract(QuadParticleRenderState output,Camera camera,float partial) {
        alpha=star==null?1:star.opacity(partial);
        var anchor=star==null?new Vec3(net.minecraft.util.Mth.lerp(partial,xo,x),net.minecraft.util.Mth.lerp(partial,yo,y),net.minecraft.util.Mth.lerp(partial,zo,z))
            :star.getPosition(partial).add(0,star.getBbHeight()/2,0);
        var back=velocity.lengthSqr()<1e-8?new Vec3(0,1,0):velocity.normalize().scale(-1);
        var relative=anchor.subtract(camera.position());
        WeatherCardGeometry.orientation(back.x,back.y,back.z,-relative.x,-relative.y,-relative.z,basis,rotation);
        // Put the star anchor at 76% of the sprite height, leaving most of the image behind it.
        var center=relative.add(back.scale(quadSize*.52));
        extractRotatedQuad(output,rotation,(float)center.x,(float)center.y,(float)center.z,partial);
    }
    @Override protected int getLightCoords(float partial) {return 15728880;}
    @Override protected Layer getLayer() {return Layer.TRANSLUCENT;}
}