package naturality.client.entities.zombies;

import java.util.WeakHashMap;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.phys.Vec2;

/** Smoothed whole-body shuffle, synchronized to the native walking cycle. */
public final class ZombieWalk {
    public static final class Motion {
        private float filtered,current,previous;
        public void tick(double speed) {
            previous=current;
            float target=(float)((speed<.003?0:speed)/(.07+Math.max(0,speed)));
            filtered+=(target-filtered)*.25F; current+=(filtered-current)*.25F;
        }
        public Vec2 pose(float partial,float walkPosition) {
            float amount=previous+(current-previous)*Math.clamp(partial,0,1);
            return new Vec2(10*amount,1.8F*amount*(float)Math.sin(walkPosition*.6662F));
        }
    }
    private static final WeakHashMap<LivingEntity,Motion> MOTIONS=new WeakHashMap<>();
    private ZombieWalk() { }
    private static boolean walking(LivingEntity entity) {
        return entity.isAlive() && !entity.isPassenger() && !entity.isSwimming();
    }
    public static void initialize() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            var level = client.level;
            if(level==null) { MOTIONS.clear(); return; }
            if(client.isPaused()) return;
            for(var e:level.entitiesForRendering()) if(e instanceof Zombie zombie) {
                double speed=Math.hypot(zombie.getX()-zombie.xo,zombie.getZ()-zombie.zo);
                if(speed>1 || zombie.horizontalCollision || !walking(zombie)) speed=0;
                MOTIONS.computeIfAbsent(zombie,_ -> new Motion()).tick(speed);
            }
        });
    }
    public static Vec2 pose(LivingEntity entity,float partial,float walkPosition) {
        if(!(entity instanceof Zombie) || !walking(entity)) return Vec2.ZERO;
        var motion=MOTIONS.get(entity);
        return motion==null?Vec2.ZERO:motion.pose(partial,walkPosition);
    }

    @org.jspecify.annotations.NullMarked
    public interface ZombieWalkState {
        Vec2 naturality$walkLean();
        void naturality$walkLean(Vec2 pose);
    }
}
