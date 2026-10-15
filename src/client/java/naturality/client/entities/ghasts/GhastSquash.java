package naturality.client.entities.ghasts;

import java.util.WeakHashMap;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.monster.Ghast;
import naturality.entities.ghasts.GhastCrash;
import naturality.entities.ghasts.GhastCrash.GhastCrashBody;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import com.mojang.blaze3d.vertex.PoseStack;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import net.minecraft.client.renderer.entity.GhastRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.GhastRenderState;
import net.minecraft.client.renderer.entity.HappyGhastRenderer;
import net.minecraft.client.renderer.entity.state.HappyGhastRenderState;
import net.minecraft.world.entity.animal.happyghast.HappyGhast;

/** Tick-based, interpolated squash with a damped elastic release. */
public final class GhastSquash {
    private static final WeakHashMap<net.minecraft.world.entity.Entity, Motion> MOTIONS = new WeakHashMap<>();
    private static ClientLevel world;
    private GhastSquash() { }

    public static final class Motion extends naturality.api.motion.GhastMotion.Motion {
        private boolean charging;
        private int chargeTicks;
        private int releaseTicks = 25;
        private float releaseAmount;
        private float previous;
        private float current;
        public void tick(boolean charge) {
            previous = current;
            if (charge) {
                if (!charging) chargeTicks = 0;
                float t = Math.min(++chargeTicks / 9.0F, 1);
                current = .12F * t * t * (3 - 2 * t);
                releaseTicks = 25;
            } else {
                if (charging) {
                    releaseAmount = current;
                    releaseTicks = 0;
                }
                int t = releaseTicks++;
                current = t >= 24 ? 0 : releaseAmount * (float)(Math.exp(-t / 5.0) * Math.cos(t * .65))
                    * Math.min(1, (24 - t) / 4.0F);
            }
            charging = charge;
        }
        public float sample(float partial) { return previous + (current - previous) * partial; }
    }

    public static void initialize() {
        naturality.client.systems.sleep.SleepVisuals.register(client -> {
            if (world != client.level) {
                MOTIONS.clear();
                world = client.level;
            }
            if (world == null || client.isPaused()) return;
            for (var entity : world.entitiesForRendering()) {
                if (entity instanceof Ghast ghast)
                    MOTIONS.computeIfAbsent(ghast, _ -> new Motion()).tick(ghast.isCharging());
            }
        });
    }

    public static float sample(Ghast ghast, float partial) {
        Motion motion = MOTIONS.get(ghast);
        return motion == null ? 0 : motion.sample(partial);
    }
    public static net.minecraft.world.phys.Vec2 lean(net.minecraft.world.entity.Entity entity, float bodyYaw, float partial) {
        // Authoritative rider offsets still need their matching body pose when physics is enabled.
        if (!naturality.config.startup.FeatureConfig.Feature.GHAST_SQUASH.enabled() && !naturality.config.startup.FeatureConfig.Feature.GHAST_PHYSICS.enabled())
            return net.minecraft.world.phys.Vec2.ZERO;
        return naturality.api.motion.GhastMotion.lean((net.minecraft.world.entity.LivingEntity)entity, bodyYaw, partial);
    }
    public static void extractLean(Ghast ghast, SquashingGhastRenderer.State state, float partial) {
        var lean = lean(ghast, state.bodyRot, partial);
        state.leanPitch = lean.x;
        state.leanRoll = lean.y;
    }

    /** Interpolated server angular dynamics, shared with collision support. */
    public record GhastCrashPose(float ticks, Vec3 rotation, float stretch) {
        private static final class Motion {
            Vec3 previous=Vec3.ZERO,current=Vec3.ZERO; int tick=-1;
        }
        private static final WeakHashMap<LivingEntity,Motion> MOTIONS=new WeakHashMap<>();
        public static GhastCrashPose extract(LivingEntity entity,float partial) {
            if (!entity.isDeadOrDying() || !(entity instanceof GhastCrashBody)) { MOTIONS.remove(entity); return null; }
            Motion motion=MOTIONS.computeIfAbsent(entity,_ -> new Motion());
            if (motion.tick!=entity.tickCount) {
                motion.previous=motion.current;
                motion.current=((GhastCrashBody)entity).naturality$crashRotation();
                motion.tick=entity.tickCount;
            }
            return new GhastCrashPose(entity.deathTime+partial,motion.previous.lerp(motion.current,partial),
                GhastCrash.fallStretch(Math.max(0,-entity.getDeltaMovement().y)));
        }
        public void apply(PoseStack pose,float bodyYaw) {
            pose.rotate(GhastCrash.modelRotation(bodyYaw,rotation));
            float sideways=(float)(1/Math.sqrt(stretch));
            pose.scale(sideways,stretch,sideways);
        }
    }

    /** Damped, length-constrained pendulums attached to the tumbling corpse. */
    public static final class GhastTentaclePhysics {
        public record Pose(float[] pitch,float[] roll) { }
        public static final class Pendulum {
            public Vec3 direction,velocity=Vec3.ZERO;
            public Pendulum(Vec3 direction) { this.direction=direction.normalize(); }
            public void tick(Vec3 force,double length) {
                for(int step=0;step<4;step++) {
                    Vec3 tangent=force.subtract(direction.scale(force.dot(direction)));
                    velocity=velocity.add(tangent.scale(.25/length)).scale(.982);
                    direction=direction.add(velocity.scale(.25)).normalize();
                    velocity=velocity.subtract(direction.scale(velocity.dot(direction)));
                }
            }
        }
        private static final class Motion {
            final Pendulum[] parts=new Pendulum[9];
            final Vec3[] previous=new Vec3[9],anchors=new Vec3[9],anchorVelocity=new Vec3[9];
            Vec3 previousVelocity=Vec3.ZERO,acceleration=Vec3.ZERO;
            int tick;
        }
        private static final WeakHashMap<LivingEntity,Motion> MOTIONS=new WeakHashMap<>();
        private GhastTentaclePhysics() { }
        private static Quaternionf frame(float yaw,Vec3 rotation) {
            return GhastCrash.worldRotation(rotation).mul(new Quaternionf().rotationY((float)Math.toRadians(180-yaw)).rotateZ((float)Math.PI));
        }
        private static Vec3 transform(Quaternionf q,Vec3 v) {
            Vector3f result=q.transform(new Vector3f((float)v.x,(float)v.y,(float)v.z));
            return new Vec3(result.x,result.y,result.z);
        }
        public static Pose extract(LivingEntity entity,float partial,GhastCrashPose crash) {
            if(crash==null) { MOTIONS.remove(entity); return null; }
            Vec3 rotation=((GhastCrashBody)entity).naturality$crashRotation();
            Quaternionf currentFrame=frame(entity.yBodyRot,rotation);
            Motion motion=MOTIONS.get(entity);
            if(motion==null) {
                motion=new Motion(); motion.tick=entity.tickCount;
                motion.previousVelocity=entity.getDeltaMovement();
                for(int i=0;i<9;i++) {
                    // Freeze the last living pose once; the sine animation never advances during death.
                    float angle=.2F*(float)Math.sin((entity.tickCount-1)*.3+i)+.4F;
                    Vec3 local=new Vec3(0,Math.cos(angle),Math.sin(angle));
                    motion.parts[i]=new Pendulum(transform(currentFrame,local));
                    motion.previous[i]=motion.parts[i].direction;
                    motion.anchors[i]=transform(currentFrame,new Vec3((i%3-1)*.9,2,(i/3-1)*.9));
                    motion.anchorVelocity[i]=Vec3.ZERO;
                }
                MOTIONS.put(entity,motion);
            }
            if(motion.tick!=entity.tickCount) {
                Vec3 velocity=entity.getDeltaMovement();
                Vec3 acceleration=velocity.subtract(motion.previousVelocity);
                if(acceleration.length()>.15) acceleration=acceleration.normalize().scale(.15);
                motion.acceleration=motion.acceleration.lerp(acceleration,.35);
                for(int i=0;i<9;i++) {
                    motion.previous[i]=motion.parts[i].direction;
                    Vec3 anchor=transform(currentFrame,new Vec3((i%3-1)*.9,2,(i/3-1)*.9));
                    Vec3 anchorVelocity=anchor.subtract(motion.anchors[i]);
                    Vec3 anchorAcceleration=anchorVelocity.subtract(motion.anchorVelocity[i]);
                    if(anchorAcceleration.length()>.12) anchorAcceleration=anchorAcceleration.normalize().scale(.12);
                    Vec3 force=new Vec3(0,-.04,0).subtract(motion.acceleration)
                        .subtract(anchorAcceleration).subtract(velocity.scale(.035));
                    motion.parts[i].tick(force,1.8+(i%3)*.3);
                    motion.anchors[i]=anchor; motion.anchorVelocity[i]=anchorVelocity;
                }
                motion.previousVelocity=velocity; motion.tick=entity.tickCount;
            }
            float[] pitch=new float[9],roll=new float[9];
            Quaternionf inverse=frame(entity.yBodyRot,crash.rotation()).conjugate();
            for(int i=0;i<9;i++) {
                Vec3 direction=motion.previous[i].lerp(motion.parts[i].direction,partial).normalize();
                Vec3 local=transform(inverse,direction);
                pitch[i]=(float)Math.atan2(local.z,Math.hypot(local.x,local.y));
                roll[i]=(float)Math.atan2(-local.x,local.y);
            }
            return new Pose(pitch,roll);
        }
    }

    public interface GhastTentacleState {
        GhastTentaclePhysics.Pose naturality$tentacles();
    }

    /** Scales the whole animated model around its body center, including tentacles. */
    public static final class SquashingGhastRenderer extends GhastRenderer {
        public static final class State extends GhastRenderState implements GhastTentacleState { public float squash, leanPitch, leanRoll; public GhastCrashPose crash; public GhastTentaclePhysics.Pose tentacles; public GhastTentaclePhysics.Pose naturality$tentacles() { return tentacles; } }
        public SquashingGhastRenderer(EntityRendererProvider.Context context) { super(context); }
        @Override public State createRenderState() { return new State(); }
        @Override public void extractRenderState(Ghast entity, GhastRenderState state, float partial) {
            super.extractRenderState(entity, state, partial);
            ((State)state).crash = GhastCrashPose.extract(entity, partial);
            ((State)state).tentacles = GhastTentaclePhysics.extract(entity,partial,((State)state).crash);
            if (((State)state).crash != null) { state.deathTime = 0; state.hasRedOverlay = entity.hurtTime > 0 || ((naturality.entities.ghasts.GhastCrash.GhastCrashBody)entity).naturality$impactTicks() >= 0; }
            ((State)state).squash = GhastSquash.sample(entity, partial);
            GhastSquash.extractLean(entity, (State)state, partial);
        }
        @Override protected net.minecraft.world.phys.AABB getBoundingBoxForCulling(Ghast entity,float partial) {
            var bounds=super.getBoundingBoxForCulling(entity,partial);
            return entity.isDeadOrDying() ? bounds.inflate(entity.getBbWidth()*2) : bounds;
        }
        @Override protected void scale(GhastRenderState state, PoseStack pose) {
            super.scale(state, pose);
            float vertical = 1 - ((State)state).squash;
            float horizontal = (float)(1 / Math.sqrt(vertical));
            float pivot = -1.8045F;
            pose.translate(0, pivot, 0);
            if (((State)state).crash != null) ((State)state).crash.apply(pose,state.bodyRot);
            else {
                pose.scale(horizontal, vertical, horizontal);
            }
            pose.translate(0, -pivot, 0);
        }
    }

    /** Whole-model movement lean, including the vanilla harness and ropes layers. */
    public static final class LeaningHappyGhastRenderer extends HappyGhastRenderer {
        public static final class State extends HappyGhastRenderState implements GhastTentacleState { public float leanPitch, leanRoll; public GhastCrashPose crash; public GhastTentaclePhysics.Pose tentacles; public GhastTentaclePhysics.Pose naturality$tentacles() { return tentacles; } }
        public LeaningHappyGhastRenderer(EntityRendererProvider.Context context) { super(context); }
        @Override public State createRenderState() { return new State(); }
        @Override public void extractRenderState(HappyGhast entity, HappyGhastRenderState state, float partial) {
            super.extractRenderState(entity, state, partial);
            ((State)state).crash = GhastCrashPose.extract(entity,partial);
            ((State)state).tentacles = GhastTentaclePhysics.extract(entity,partial,((State)state).crash);
            if (((State)state).crash != null) { state.deathTime = 0; state.hasRedOverlay = entity.hurtTime > 0 || ((naturality.entities.ghasts.GhastCrash.GhastCrashBody)entity).naturality$impactTicks() >= 0; }
            var lean = GhastSquash.lean(entity, state.bodyRot, partial);
            ((State)state).leanPitch = lean.x;
            ((State)state).leanRoll = lean.y;
        }
        @Override protected net.minecraft.world.phys.AABB getBoundingBoxForCulling(HappyGhast entity,float partial) {
            var bounds=super.getBoundingBoxForCulling(entity,partial);
            return entity.isDeadOrDying() ? bounds.inflate(entity.getBbWidth()*2) : bounds;
        }
        @Override protected void scale(HappyGhastRenderState state, PoseStack pose) {
            super.scale(state, pose);
            // Native mesh is scaled 4x about its feet; babies add the .2375 mesh transform.
            float pivot = -2.004F * (state.isBaby ? .2375F : 1);
            pose.translate(0, pivot, 0);
            if (((State)state).crash != null) ((State)state).crash.apply(pose,state.bodyRot);
            pose.translate(0, -pivot, 0);
        }
    }
}
