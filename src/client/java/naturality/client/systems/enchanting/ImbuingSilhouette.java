package naturality.client.systems.enchanting;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.render.GuiItemAtlas;
import net.minecraft.client.renderer.state.gui.GuiItemRenderState;
import org.jspecify.annotations.Nullable;

/** Reads the rendered item alpha and fits a conservative rotated text rectangle inside it. */
@org.jspecify.annotations.NullMarked
public final class ImbuingSilhouette {
    private static final int SIZE = 32;
    private @Nullable Object identity;
    private @Nullable Object texture;
    private boolean @Nullable [] mask;
    private boolean pending;
    private int lastWidth = -1;
    private @Nullable Line line;
    public record Line(float x, float y, float angle, float scale) {}

    public void capture(GuiItemRenderState item, GuiItemAtlas.SlotView slot) {
        Object model = item.itemStackRenderState().getModelIdentity();
        if (pending || model.equals(identity) && slot.textureView() == texture) return;
        identity = model;
        texture = slot.textureView();
        mask = null;
        line = null;
        pending = true;
        var source = slot.textureView().texture();
        int width = source.getWidth(0), height = source.getHeight(0);
        int left = Math.round(slot.u0() * width), bottom = Math.round(slot.v1() * height);
        int side = Math.round((slot.u1() - slot.u0()) * width);
        var buffer = RenderSystem.getDevice().createBuffer(() -> "Imbuing silhouette", 9, (long)width * height * 4);
        RenderSystem.getDevice().createCommandEncoder().copyTextureToBuffer(source, buffer, 0L, () -> {
            try (buffer; var read = buffer.map(true, false)) {
                boolean[] pixels = new boolean[SIZE * SIZE];
                for (int y = 0; y < SIZE; y++) for (int x = 0; x < SIZE; x++) {
                    boolean opaque = true;
                    int x0 = x * side / SIZE, x1 = Math.max(x0 + 1, (int)Math.ceil((x + 1) * side / (double)SIZE));
                    int y0 = y * side / SIZE, y1 = Math.max(y0 + 1, (int)Math.ceil((y + 1) * side / (double)SIZE));
                    for (int sy = y0; sy < y1 && opaque; sy++) for (int sx = x0; sx < x1; sx++) {
                        int offset = ((bottom + side - 1 - sy) * width + left + sx) * 4;
                        if ((read.data().get(offset + 3) & 255) != 255) { opaque = false; break; }
                    }
                    pixels[y * SIZE + x] = opaque;
                }
                mask = pixels;
                lastWidth = -1;
            } finally { pending = false; }
        }, 0);
    }

    public @Nullable Line fit(int textWidth, int textHeight) {
        var pixels = mask;
        if (pixels == null || textWidth <= 0) return null;
        if (lastWidth == textWidth) return line;
        lastWidth = textWidth;
        line = null;
        float best = 0;
        // Include a one-font-pixel margin on every side for glyph bearings.
        float w = textWidth + 2, h = textHeight + 2;
        for (int degrees = -90; degrees < 90; degrees += 5) {
            float angle = (float)Math.toRadians(degrees), c = (float)Math.cos(angle), s = (float)Math.sin(angle);
            for (int y = 0; y < SIZE; y++) for (int x = 0; x < SIZE; x++) {
                if (!pixels[y * SIZE + x]) continue;
                float cx = x + .5f, cy = y + .5f;
                if (!fits(pixels, cx, cy, c, s, w * best / 2, h * best / 2)) continue;
                float lo = best, hi = SIZE * 1.415f / w;
                for (int step = 0; step < 7; step++) {
                    float mid = (lo + hi) / 2;
                    if (fits(pixels, cx, cy, c, s, w * mid / 2, h * mid / 2)) lo = mid; else hi = mid;
                }
                if (lo > best) { best = lo; line = new Line(cx * 3, cy * 3, angle, lo * 3); }
            }
        }
        return line;
    }

    private static boolean fits(boolean[] pixels, float cx, float cy, float c, float s, float hw, float hh) {
        float ex = Math.abs(c) * hw + Math.abs(s) * hh, ey = Math.abs(s) * hw + Math.abs(c) * hh;
        if (cx - ex < 0 || cy - ey < 0 || cx + ex > SIZE || cy + ey > SIZE) return false;
        // Expanded local bounds conservatively include every pixel square touched by the rectangle.
        float pad = (Math.abs(c) + Math.abs(s)) * .5f;
        for (int y = Math.max(0, (int)(cy - ey)); y < Math.min(SIZE, Math.ceil(cy + ey)); y++)
            for (int x = Math.max(0, (int)(cx - ex)); x < Math.min(SIZE, Math.ceil(cx + ex)); x++) {
                float dx = x + .5f - cx, dy = y + .5f - cy;
                if (Math.abs(c * dx + s * dy) <= hw + pad && Math.abs(-s * dx + c * dy) <= hh + pad
                        && !pixels[y * SIZE + x]) return false;
            }
        return true;
    }
}

