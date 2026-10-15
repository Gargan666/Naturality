package naturality.client.particle;

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.WeakHashMap;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** Render-thread-only snapshots. Values never retain their entity/world keys. */
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
}
