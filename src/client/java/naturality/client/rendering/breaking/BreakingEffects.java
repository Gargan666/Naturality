package naturality.client.rendering.breaking;

import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.core.BlockPos;

/** Applies position-specific breaking textures to the current submission. */
@org.jspecify.annotations.NullMarked
public final class BreakingEffects {
    private static final ThreadLocal<Integer> CURRENT = ThreadLocal.withInitial(() -> -1);
    private BreakingEffects() { }
    public static void at(BlockPos pos, Runnable submission) {
        int previous = CURRENT.get();
        CURRENT.set(BreakingTextures.variant(pos));
        try { submission.run(); }
        finally { CURRENT.set(previous); }
    }

    public static void select(BlockPos pos) { CURRENT.set(BreakingTextures.variant(pos)); }

    public static RenderType current(int stage, boolean oit, RenderType fallback) {
        return BreakingTextures.texture(stage, oit, fallback, CURRENT.get());
    }

    /** Per-particle texture crop and pixel holes for vanilla terrain fragments. */
    public interface BlockFragmentPixels {
        int naturality$cropStartX();
        int naturality$cropStartY();
        int naturality$cropWidth();
        int naturality$cropHeight();
        boolean naturality$isPixelRemoved(int x, int y);
    }
}
