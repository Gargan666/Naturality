package naturality.client.entities.fish;

import java.util.WeakHashMap;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.world.entity.animal.fish.AbstractFish;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public final class FishSwim {
    public record Pose(float yaw,float pitch,float phase) { }
    public static final class Motion {
        private float yaw,pitch,oldYaw,oldPitch,phase,oldPhase,rate,filtered;
        public Motion(float yaw,float pitch,float age) { this.yaw=oldYaw=yaw;this.pitch=oldPitch=pitch;phase=oldPhase=age; }
        public void tick(Vec3 movement) {
            oldYaw=yaw;oldPitch=pitch;oldPhase=phase;
            double speed=movement.length();
            float target=speed<.003?0:(float)Math.min(1.5,speed/.06);
            filtered+=(target-filtered)*.2F;rate+=(filtered-rate)*.2F;
            if(target==0 && rate<.0001F) { rate=0;filtered=0; }
            phase+=rate;
            if(speed>.003) {
                if(movement.horizontalDistance()>.001) yaw+=Mth.wrapDegrees((float)Math.toDegrees(Math.atan2(-movement.x,movement.z))-yaw)*.2F;
                float targetPitch=(float)-Math.toDegrees(Math.atan2(movement.y,movement.horizontalDistance()));
                pitch+=(targetPitch-pitch)*.2F;
            }
        }
        public void idleLook(float targetYaw,float targetPitch) {
            yaw+=Mth.wrapDegrees(targetYaw-yaw)*.08F;
            pitch+=(targetPitch-pitch)*.08F;
        }
        public Pose sample(float partial) { return new Pose(oldYaw+Mth.wrapDegrees(yaw-oldYaw)*partial,oldPitch+(pitch-oldPitch)*partial,oldPhase+(phase-oldPhase)*partial); }
    }
    private static final WeakHashMap<AbstractFish,Motion> MOTIONS=new WeakHashMap<>();
    private FishSwim() { }
    public static void initialize() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            var level = client.level;
            if(level==null) { MOTIONS.clear();return; }
            if(client.isPaused()) return;
            for(var e:level.entitiesForRendering()) if(e instanceof AbstractFish fish) {
                var motion=MOTIONS.computeIfAbsent(fish,_ -> new Motion(fish.getYRot(),fish.getXRot(),fish.tickCount));
                Vec3 movement=fish.position().subtract(new Vec3(fish.xo,fish.yo,fish.zo));
                if(movement.lengthSqr()>16 || !fish.isInWater() || !fish.isAlive()) movement=Vec3.ZERO;
                boolean drifting=movement.length()<.012;
                motion.tick(drifting?Vec3.ZERO:movement);
                if(drifting && fish.isInWater() && fish.isAlive()) motion.idleLook(fish.getYRot(),fish.getXRot());
            }
        });
    }
    public static Pose pose(AbstractFish fish,float partial) {
        var motion=MOTIONS.get(fish);return motion==null?null:motion.sample(partial);
    }

    @org.jspecify.annotations.NullMarked
    public interface FishSwimState {
        FishSwim.@org.jspecify.annotations.Nullable Pose naturality$swim();
        void naturality$swim(FishSwim.@org.jspecify.annotations.Nullable Pose pose);
    }
}
