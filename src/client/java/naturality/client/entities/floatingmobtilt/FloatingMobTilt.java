package naturality.client.entities.floatingmobtilt;

import naturality.api.motion.GhastMotion;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec2;

/** Client-only movement lean for the user-configured living entity types. */
public final class FloatingMobTilt {
    private FloatingMobTilt() { }
    public static void initialize() {
        naturality.config.live.NaturalityConfig.FloatingMobTiltConfig.get();
        naturality.client.systems.sleep.SleepVisuals.register(client -> {
            var level = client.level;
            if(level==null || client.isPaused()) return;
            for(var entity:level.entitiesForRendering()) if(entity instanceof LivingEntity living && enabled(living)) {
                // Ghast physics already ticks this same smoothing state on both sides.
                if(GhastMotion.supported(living) && naturality.config.startup.FeatureConfig.Feature.GHAST_PHYSICS.enabled()) continue;
                GhastMotion.tick(living);
            }
        });
    }
    public static boolean enabled(LivingEntity entity) {
        return naturality.config.live.NaturalityConfig.FloatingMobTiltConfig.enabled(entity);
    }
    public static Vec2 lean(LivingEntity entity,float yaw,float partial) {
        if(!enabled(entity) || !entity.isAlive()) return Vec2.ZERO;
        var surface = naturality.systems.swimming.WaterSurface.state(entity);
        if(naturality.systems.swimming.WaterSurface.active(entity) || surface.lean.lengthSqr() > 1.0e-8
                || surface.previousLean.lengthSqr() > 1.0e-8) return Vec2.ZERO;
        return GhastMotion.lean(entity,yaw,partial);
    }

    @org.jspecify.annotations.NullMarked
    public interface FloatingMobTiltState {
        Vec2 naturality$floatLean();
        void naturality$floatLean(Vec2 lean);
    }
}
