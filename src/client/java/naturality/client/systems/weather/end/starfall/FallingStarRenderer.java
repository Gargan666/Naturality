package naturality.client.systems.weather.end.starfall;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import naturality.Naturality;
import naturality.systems.weather.end.starfall.StarfallWeather.FallingStar;
import naturality.client.api.geometry.ParticleGeometry;
import naturality.client.systems.weather.wind.WindRendering.WindParticleControl;
import java.util.WeakHashMap;
import naturality.bootstrap.ContentRegistration.NaturalityParticles;
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

public final class FallingStarRenderer extends EntityRenderer<FallingStar,FallingStarRenderer.State> {
    private static final ModelPart MODEL=model();
    public static final class State extends EntityRenderState { public float yaw,pitch,opacity; }
    public FallingStarRenderer(EntityRendererProvider.Context context) {super(context);}
    private static ModelPart model() {
        var mesh=new MeshDefinition();
        mesh.getRoot().addOrReplaceChild("star",CubeListBuilder.create()
            .texOffs(0,20).addBox(-4,-4,-4,8,8,8)
            .texOffs(0,0).addBox(-5,-5,-5,10,10,10),PartPose.ZERO);
        return LayerDefinition.create(mesh,64,64).bakeRoot();
    }
    @Override public State createRenderState() {return new State();}
    @Override public void extractRenderState(FallingStar star,State state,float partial) {
        super.extractRenderState(star,state,partial);
        var velocity=star.getDeltaMovement();
        state.yaw=(float)Math.toDegrees(Math.atan2(velocity.x,velocity.z));
        state.pitch=(float)-Math.toDegrees(Math.atan2(velocity.y,velocity.horizontalDistance()));
        state.displayFireAnimation=false;state.opacity=star.opacity(partial);
    }
    @Override public void submit(State state,PoseStack pose,SubmitNodeCollector collector,CameraRenderState camera) {
        if(!state.isInvisible && state.opacity>0) {
            pose.pushPose();pose.translate(0,state.boundingBoxHeight/2,0);
            pose.rotate(Axis.YP.rotationDegrees(-state.yaw));pose.rotate(Axis.XP.rotationDegrees(state.pitch));
            pose.rotate(Axis.YP.rotationDegrees(-state.ageInTicks*6));pose.rotate(Axis.XP.rotationDegrees(state.ageInTicks*9));
            pose.scale(-2,-2,2);
            collector.submitCustomGeometry(pose,RenderTypes.entityTranslucent(Naturality.id("textures/entity/star.png")),(submitted,buffer)->{
                var modelPose=new PoseStack();modelPose.last().pose().set(submitted.pose());modelPose.last().normal().set(submitted.normal());
                MODEL.render(modelPose,buffer,15728880,OverlayTexture.NO_OVERLAY,net.minecraft.util.ARGB.colorFromFloat(state.opacity,1,1,1));
            });
            pose.popPose();
        }
        super.submit(state,pose,collector,camera);
    }

    /** One card attached to the star center. Its Y axis trails velocity and only its roll faces the observer. */
    @org.jspecify.annotations.NullMarked
    public static final class StarTrailParticle extends SingleQuadParticle {
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
            naturality.client.systems.sleep.SleepVisuals.register(client->{
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
            } else {if(++age>=lifetime){remove();return;}double speed=naturality.client.systems.sleep.SleepVisuals.speed();setPos(x+velocity.x*speed,y+velocity.y*speed,z+velocity.z*speed);}
        }
        @Override public void extract(QuadParticleRenderState output,Camera camera,float partial) {
            var currentStar = star;
            alpha=currentStar==null?1:currentStar.opacity(partial);
            var anchor=currentStar==null?new Vec3(net.minecraft.util.Mth.lerp(partial,xo,x),net.minecraft.util.Mth.lerp(partial,yo,y),net.minecraft.util.Mth.lerp(partial,zo,z))
                :currentStar.getPosition(partial).add(0,currentStar.getBbHeight()/2,0);
            var back=velocity.lengthSqr()<1e-8?new Vec3(0,1,0):velocity.normalize().scale(-1);
            var relative=anchor.subtract(camera.position());
            ParticleGeometry.orientation(back.x,back.y,back.z,-relative.x,-relative.y,-relative.z,basis,rotation);
            // Put the currentStar anchor at 76% of the sprite height, leaving most of the image behind it.
            var center=relative.add(back.scale(quadSize*.52));
            extractRotatedQuad(output,rotation,(float)center.x,(float)center.y,(float)center.z,partial);
        }
        @Override protected int getLightCoords(float partial) {return 15728880;}
        @Override protected Layer getLayer() {return Layer.TRANSLUCENT;}
    }
}
