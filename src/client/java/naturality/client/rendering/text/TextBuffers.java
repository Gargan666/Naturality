package naturality.client.rendering.text;

import java.lang.reflect.Field;
import java.util.Optional;
import net.minecraft.client.renderer.rendertype.RenderType;

/** Keeps ImmediatelyFast's glyph lookup cache within one text submission. */
@org.jspecify.annotations.NullMarked
public final class TextBuffers {
    private static final ClassValue<Optional<Field>> LAST_RENDER_TYPE = new ClassValue<>() {
        @Override protected Optional<Field> computeValue(@org.jspecify.annotations.Nullable Class<?> type) {
            java.util.Objects.requireNonNull(type);
            try {
                var field = type.getDeclaredField("immediatelyFast$lastRenderType");
                if (field.getType() != RenderType.class)
                    throw new IllegalStateException("Unexpected ImmediatelyFast text-cache field type");
                if (!field.trySetAccessible())
                    throw new IllegalStateException("Cannot access ImmediatelyFast text-cache boundary");
                return Optional.of(field);
            } catch (NoSuchFieldException absent) {
                // ImmediatelyFast can be installed with fast_text_lookup disabled.
                // Its optional mixin then adds no cache field to the glyph visitor.
                return Optional.empty();
            }
        }
    };

    private TextBuffers() {}

    public static void beginText(Object glyphRenderer) {
        var field = LAST_RENDER_TYPE.get(glyphRenderer.getClass());
        if (field.isEmpty()) return;
        try {
            // Essential's preceding icon/padding draw can retire the previous
            // BufferBuilder. The first glyph must reacquire it; later glyphs in
            // this text retain ImmediatelyFast's normal consecutive-type cache.
            field.get().set(glyphRenderer, null);
        } catch (IllegalAccessException failure) {
            throw new IllegalStateException("Cannot reset ImmediatelyFast text-cache boundary", failure);
        }
    }
}
