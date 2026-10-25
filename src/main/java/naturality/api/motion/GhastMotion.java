package naturality.api.motion;

import com.google.common.collect.MapMaker;
import java.util.Map;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.animal.happyghast.HappyGhast;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.Vec2;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Shared authoritative/client easing for model lean and rider attachment points. */
public final class GhastMotion {
    // Entity equality uses its network ID, shared by client/server counterparts and reused
    // across worlds. Weak identity keys keep each body's history separate without retaining it.
    // MapMaker's concurrent map protects bookkeeping; each Motion stays on its entity's thread.
    private static final Map<LivingEntity, Motion> MOTIONS = new MapMaker().weakKeys().makeMap();
    private GhastMotion() { }
    public static boolean supported(Object entity) { return entity instanceof Ghast || entity instanceof HappyGhast; }
    public static class Motion {
        private float x, z, previousX, previousZ, filteredX, filteredZ;
        public void tickMovement(double vx, double vz) {
            double factor=8/(.12+Math.hypot(vx,vz));
            float targetX=(float)(vx*factor), targetZ=(float)(vz*factor);
            previousX=x; previousZ=z;
            // Two persistent low-pass stages damp velocity noise without restarting easing.
            // Every stage is a convex blend, so lean stays bounded and cannot overshoot.
            filteredX+=(targetX-filteredX)*.25F;
            filteredZ+=(targetZ-filteredZ)*.25F;
            x+=(filteredX-x)*.25F;
            z+=(filteredZ-z)*.25F;
        }
        public float leanX(float partial) { return previousX+(x-previousX)*Math.clamp(partial,0,1); }
        public float leanZ(float partial) { return previousZ+(z-previousZ)*Math.clamp(partial,0,1); }
    }
    public static double expoInOut(double progress) {
        double t=Math.clamp(progress,0,1);
        if (t==0 || t==1) return t;
        return t<.5 ? Math.pow(2,20*t-10)/2 : (2-Math.pow(2,-20*t+10))/2;
    }
    public static void tick(LivingEntity entity) {
        if (!naturality.config.live.NaturalityConfig.FloatingMobTiltConfig.enabled(entity)) return;
        var velocity = entity.isAlive() && !entity.isPassenger() ? entity.getDeltaMovement() : Vec3.ZERO;
        MOTIONS.computeIfAbsent(entity, _ -> new Motion()).tickMovement(velocity.x, velocity.z);
    }
    public static Vec2 lean(LivingEntity entity, float yawDegrees, float partial) {
        if (!naturality.config.live.NaturalityConfig.FloatingMobTiltConfig.enabled(entity)) return Vec2.ZERO;
        Motion motion = MOTIONS.get(entity);
        if (motion == null) return Vec2.ZERO;
        double yaw = Math.toRadians(yawDegrees);
        float x = motion.leanX(partial), z = motion.leanZ(partial);
        return new Vec2((float)(-x * Math.sin(yaw) + z * Math.cos(yaw)),
            (float)(x * Math.cos(yaw) + z * Math.sin(yaw)));
    }
    public static double bodyCenter(LivingEntity entity) {
        return (entity instanceof Ghast ? 1.8045 : 2.004 * (entity.isBaby() ? .2375 : 1)) * entity.getScale();
    }
    public static Vec3 seat(HappyGhast entity, Vec3 seat) {
        Vec2 tilt = lean(entity, entity.yBodyRot, 1);
        Vec3 center = entity.position().add(0, bodyCenter(entity), 0);
        Vec3 offset = seat.subtract(center);
        Quaternionf frame = new Quaternionf().rotationY((float)Math.toRadians(180 - entity.yBodyRot)).rotateZ((float)Math.PI);
        Quaternionf rotation = new Quaternionf(frame)
            .rotateX((float)Math.toRadians(tilt.x)).rotateZ((float)Math.toRadians(tilt.y))
            .mul(new Quaternionf(frame).conjugate());
        Vector3f rotated = rotation.transform(new Vector3f((float)offset.x, (float)offset.y, (float)offset.z));
        return center.add(rotated.x, rotated.y, rotated.z);
    }
}


