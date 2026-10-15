package naturality.client.particle;

import java.util.WeakHashMap;
import naturality.NaturalityParticles;
import naturality.starfall.FallingStar;
import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;
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
    private static @org.jspecify.annotations.Nullable SpriteSet sprites;
    private static @org.jspecify.annotations.Nullable ClientLevel lastLevel;
    private static final WeakHashMap<FallingStar,StarTrailParticle> attached=new WeakHashMap<>();
    private final @org.jspecify.annotations.Nullable FallingStar star;
    private final Matrix3f basis=new Matrix3f();
    private final Quaternionf rotation=new Quaternionf();
    private Vec3 velocity;
    private long lastTick;
    public static void initialize() {
        ParticleProviderRegistry.getInstance().register(NaturalityParticles.STAR_TRAIL,set->{
            sprites=set;
            return (_,level,x,y,z,vx,vy,vz,_)->new StarTrailParticle(level,x,y,z,new Vec3(vx,vy,vz),set.first(),null);
        });
        naturality.client.sleep.SleepVisuals.register(client->{
            var level = client.level; var currentSprites = sprites;
            if(level!=lastLevel) {attached.clear();lastLevel=level;}
            if(level==null || currentSprites==null)return;
            attached.entrySet().removeIf(entry->entry.getKey().isRemoved());
            for(var entity:level.entitiesForRendering())if(entity instanceof FallingStar star) {
                var old=attached.get(star);
                if(old!=null && old.isAlive() && level.getGameTime()-old.lastTick<=2)continue;
                var p=new StarTrailParticle(level,star.getX(),star.getY()+star.getBbHeight()/2,star.getZ(),star.getDeltaMovement(),currentSprites.first(),star);
                WindParticleControl.mark(p,true);client.particleEngine.add(p);attached.put(star,p);
            }
        });
    }
    private StarTrailParticle(ClientLevel level,double x,double y,double z,Vec3 velocity,TextureAtlasSprite sprite,@org.jspecify.annotations.Nullable FallingStar star) {
        super(level,x,y,z,sprite);this.star=star;this.velocity=velocity;hasPhysics=false;quadSize=3;lifetime=25;
        lastTick=level.getGameTime();
    }
    @Override public void tick() {
        var currentStar = star;
        lastTick=level.getGameTime();xo=x;yo=y;zo=z;
        if(currentStar!=null) {
            if(currentStar.isRemoved()) {remove();return;}
            setPos(currentStar.getX(),currentStar.getY()+currentStar.getBbHeight()/2,currentStar.getZ());velocity=currentStar.getDeltaMovement();
        } else {if(++age>=lifetime){remove();return;}double speed=naturality.client.sleep.SleepVisuals.speed();setPos(x+velocity.x*speed,y+velocity.y*speed,z+velocity.z*speed);}
    }
    @Override public void extract(QuadParticleRenderState output,Camera camera,float partial) {
        var currentStar = star;
        alpha=currentStar==null?1:currentStar.opacity(partial);
        var anchor=currentStar==null?new Vec3(net.minecraft.util.Mth.lerp(partial,xo,x),net.minecraft.util.Mth.lerp(partial,yo,y),net.minecraft.util.Mth.lerp(partial,zo,z))
            :currentStar.getPosition(partial).add(0,currentStar.getBbHeight()/2,0);
        var back=velocity.lengthSqr()<1e-8?new Vec3(0,1,0):velocity.normalize().scale(-1);
        var relative=anchor.subtract(camera.position());
        WeatherCardGeometry.orientation(back.x,back.y,back.z,-relative.x,-relative.y,-relative.z,basis,rotation);
        // Put the currentStar anchor at 76% of the sprite height, leaving most of the image behind it.
        var center=relative.add(back.scale(quadSize*.52));
        extractRotatedQuad(output,rotation,(float)center.x,(float)center.y,(float)center.z,partial);
    }
    @Override protected int getLightCoords(float partial) {return 15728880;}
    @Override protected Layer getLayer() {return Layer.TRANSLUCENT;}
}
