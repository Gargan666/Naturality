package naturality.client.api.rendering;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.MutableQuadView;

/** Shared optional renderer access for biome tint and displaced coating lighting. */
@org.jspecify.annotations.NullMarked
public final class RendererQuads {
    private RendererQuads() {}

    /** Sodium owns and reuses this FRAPI view; no per-vertex reflective calls or wrappers. */
    public static MutableQuadView sodium(Object quad) {
        try {
            return (MutableQuadView) Sodium.WRAPPER.get(quad.getClass()).invokeExact(quad);
        } catch (RuntimeException | Error failure) {
            throw failure;
        } catch (Throwable failure) {
            throw new IllegalStateException("Cannot access Sodium's terrain quad view", failure);
        }
    }

    private static final class Sodium {
        private static final ClassValue<MethodHandle> WRAPPER = new ClassValue<>() {
            @Override protected MethodHandle computeValue(@org.jspecify.annotations.Nullable Class<?> type) {
                java.util.Objects.requireNonNull(type);
                try {
                    return MethodHandles.publicLookup().unreflect(type.getMethod("getWrapper"))
                        .asType(MethodType.methodType(MutableQuadView.class, Object.class));
                } catch (NoSuchMethodException | IllegalAccessException failure) {
                    throw new IllegalStateException("Unsupported Sodium terrain quad view", failure);
                }
            }
        };
    }
}
