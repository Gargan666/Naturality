package naturality.client.entity;

import java.util.WeakHashMap;
import naturality.ghast.GhastCrash;
import naturality.ghast.GhastCrashBody;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import com.mojang.blaze3d.vertex.PoseStack;


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
