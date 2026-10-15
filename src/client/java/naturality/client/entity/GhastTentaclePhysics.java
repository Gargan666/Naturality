package naturality.client.entity;

import java.util.WeakHashMap;
import naturality.ghast.GhastCrash;
import naturality.ghast.GhastCrashBody;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Damped, length-constrained pendulums attached to the tumbling corpse. */
public final class GhastTentaclePhysics {
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
