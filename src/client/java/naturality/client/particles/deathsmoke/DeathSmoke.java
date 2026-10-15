package naturality.client.particles.deathsmoke;

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.WeakHashMap;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ExplodeParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.ParticleTypes;

/** Render-thread-only snapshots. Values never retain their entity/world keys. */
@org.jspecify.annotations.NullMarked
public final class DeathSmoke {
    private static final WeakHashMap<LivingEntityRenderState, WeakReference<LivingEntity>> OWNERS = new WeakHashMap<>();
    private static final WeakHashMap<LivingEntity, Shape> SHAPES = new WeakHashMap<>();
    public record Shape(List<Vec3> points, float radius, int tick) {}
    public static @org.jspecify.annotations.Nullable LivingEntity spawning;
    public static int index;
    private DeathSmoke() {}
    public static void owner(LivingEntityRenderState state, LivingEntity entity) {
        if (entity.deathTime > 0) OWNERS.put(state, new WeakReference<>(entity));
        else OWNERS.remove(state);
    }
    public static @org.jspecify.annotations.Nullable LivingEntity owner(LivingEntityRenderState state) {
        var reference = OWNERS.get(state);
        return reference == null ? null : reference.get();
    }
    public static void capture(LivingEntity entity, List<Vec3> points, float radius) {
        SHAPES.put(entity, new Shape(List.copyOf(points), radius, entity.tickCount));
    }
    public static @org.jspecify.annotations.Nullable Shape shape(LivingEntity entity) {
        var shape = SHAPES.get(entity);
        return shape != null && entity.tickCount - shape.tick <= 4 ? shape : null;
    }
    public static float coverage(int age) {
        float t = Math.clamp((age - 2) / 6F, 0F, 1F);
        return 1 - t * t * (3 - 2 * t);
    }

    /** Native poof motion starting directly on the last rendered corpse. */
    public static final class DeathSmokeParticle extends ExplodeParticle {
        private float nativeSize;
        private float coverSize;
        public static void initialize() {
            ParticleProviderRegistry.getInstance().register(ParticleTypes.POOF, sprites ->
                (type, level, x, y, z, vx, vy, vz, random) -> {
                    var entity = DeathSmoke.spawning;
                    var shape = entity == null ? null : DeathSmoke.shape(entity);
                    boolean ghast = naturality.api.motion.GhastMotion.supported(entity);
                    if (shape == null && !ghast) return new ExplodeParticle.Provider(sprites).createParticle(type, level, x, y, z, vx, vy, vz, random);
                    int index = DeathSmoke.index++;
                    int count = ghast ? 30 : 20;
                    var point = shape == null ? new Vec3(x,y,z) : shape.points().get(Math.min(shape.points().size()-1, (int)((index+.5)*shape.points().size()/count)));
                    float radius = shape == null ? .65F : shape.radius();
                    if (ghast && entity != null) radius = Math.max(radius,entity.getBbWidth()*.35F);
                    var puff = new DeathSmokeParticle(level, vx, vy, vz, sprites, point, radius);
                    if (ghast && index%3==0) { puff.nativeSize*=1.75F; puff.coverSize*=1.75F; puff.quadSize=puff.coverSize; }
                    // Keep ghast coverage (including the larger third) for the whole animation.
                    if (ghast) puff.nativeSize = puff.coverSize;
                    return puff;
                });
        }
        public DeathSmokeParticle(ClientLevel level, double vx, double vy, double vz,
                                  SpriteSet sprites, Vec3 origin, float radius) {
            super(level, origin.x, origin.y, origin.z, vx, vy, vz, sprites);
            nativeSize = quadSize;
            coverSize = Math.max(nativeSize, radius);
            quadSize = coverSize;
        }
        @Override public void tick() {
            super.tick();
            float after = DeathSmoke.coverage(age);
            quadSize = nativeSize + (coverSize-nativeSize)*after;
        }
    }

    @org.jspecify.annotations.NullUnmarked
    public interface MobDeathState {
        int naturality$deathPixels();
        void naturality$deathPixels(int progress);
    }
}
