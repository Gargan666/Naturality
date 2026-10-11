package naturality.client.entity;

import java.util.WeakHashMap;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.monster.Ghast;

/** Tick-based, interpolated squash with a damped elastic release. */
public final class GhastSquash {
    private static final WeakHashMap<net.minecraft.world.entity.Entity, Motion> MOTIONS = new WeakHashMap<>();
    private static ClientLevel world;
    private GhastSquash() { }

    public static final class Motion extends naturality.ghast.GhastMotion.Motion {
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
        naturality.client.sleep.SleepVisuals.register(client -> {
            if (world != client.level) {
                MOTIONS.clear();
                world = client.level;
            }
            if (world == null || client.isPaused()) return;
            for (var entity : world.entitiesForRendering()) {
                if (entity instanceof Ghast ghast)
                    MOTIONS.computeIfAbsent(ghast, ignored -> new Motion()).tick(ghast.isCharging());
            }
        });
    }

    public static float sample(Ghast ghast, float partial) {
        Motion motion = MOTIONS.get(ghast);
        return motion == null ? 0 : motion.sample(partial);
    }
    public static net.minecraft.world.phys.Vec2 lean(net.minecraft.world.entity.Entity entity, float bodyYaw, float partial) {
        // Authoritative rider offsets still need their matching body pose when physics is enabled.
        if (!naturality.config.Feature.GHAST_SQUASH.enabled() && !naturality.config.Feature.GHAST_PHYSICS.enabled())
            return net.minecraft.world.phys.Vec2.ZERO;
        return naturality.ghast.GhastMotion.lean((net.minecraft.world.entity.LivingEntity)entity, bodyYaw, partial);
    }
    public static void extractLean(Ghast ghast, SquashingGhastRenderer.State state, float partial) {
        var lean = lean(ghast, state.bodyRot, partial);
        state.leanPitch = lean.x;
        state.leanRoll = lean.y;
    }
}


