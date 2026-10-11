package naturality.client.entity;

import naturality.ghast.GhastMotion;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec2;

/** Client-only movement lean for the user-configured living entity types. */
public final class FloatingMobTilt {
    private FloatingMobTilt() { }
    public static void initialize() {
        naturality.config.FloatingMobTiltConfig.get();
        naturality.client.sleep.SleepVisuals.register(client -> {
            if(client.level==null || client.isPaused()) return;
            for(var entity:client.level.entitiesForRendering()) if(entity instanceof LivingEntity living && enabled(living)) {
                // Ghast physics already ticks this same smoothing state on both sides.
                if(GhastMotion.supported(living) && naturality.config.Feature.GHAST_PHYSICS.enabled()) continue;
                GhastMotion.tick(living);
            }
        });
    }
    public static boolean enabled(LivingEntity entity) {
        return naturality.config.FloatingMobTiltConfig.enabled(entity);
    }
    public static Vec2 lean(LivingEntity entity,float yaw,float partial) {
        if(!enabled(entity) || !entity.isAlive()) return Vec2.ZERO;
        return GhastMotion.lean(entity,yaw,partial);
    }
}
