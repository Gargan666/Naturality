package naturality.client.breaking;

import com.mojang.blaze3d.platform.NativeImage;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import naturality.Naturality;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;

/** Pixel-exact rearrangements of the loaded damage stages, shared by block position. */
public final class BreakingTextures {
    public static final int VARIANTS = 32;
    private static final ThreadLocal<Integer> CURRENT = ThreadLocal.withInitial(() -> -1);
    private static final Map<Integer, RenderType> TYPES = new HashMap<>();
    private static final Map<Integer, NativeImage> SOURCES = new HashMap<>();
    private static final java.util.Set<Integer> FAILED = new java.util.HashSet<>();

    private BreakingTextures() { }

    public static int variant(BlockPos pos) {
        long hash = pos.asLong();
        hash = (hash ^ (hash >>> 30)) * 0xbf58476d1ce4e5b9L;
        hash = (hash ^ (hash >>> 27)) * 0x94d049bb133111ebL;
        return (int) (hash ^ (hash >>> 31)) & (VARIANTS - 1);
    }

    public static void at(BlockPos pos, Runnable submission) {
        int previous = CURRENT.get();
        CURRENT.set(variant(pos));
        try { submission.run(); }
        finally { CURRENT.set(previous); }
    }

    public static void select(BlockPos pos) { CURRENT.set(variant(pos)); }

    public static RenderType current(int stage, boolean oit, RenderType fallback) {
        int variant = CURRENT.get();
        if (variant < 0 || stage < 0 || stage > 9) return fallback;
        int sourceKey = stage + (oit ? 10 : 0);
        int key = sourceKey * VARIANTS + variant;
        RenderType cached = TYPES.get(key);
        if (cached != null) return cached;
        if (FAILED.contains(sourceKey)) return fallback;
        var client = Minecraft.getInstance();
        NativeImage source = SOURCES.get(sourceKey);
        if (source == null) {
            var path = Identifier.withDefaultNamespace("textures/block/destroy_" + (oit ? "oit_" : "") + "stage_" + stage + ".png");
            try (var stream = client.getResourceManager().open(path)) {
                source = NativeImage.read(stream);
                if (source.getWidth() != source.getHeight()) {
                    source.close();
                    FAILED.add(sourceKey);
                    return fallback; // Animated/non-square packs retain their original renderer.
                }
                SOURCES.put(sourceKey, source);
            } catch (IOException exception) {
                FAILED.add(sourceKey);
                Naturality.LOGGER.warn("Cannot generate breaking variants for {}", path, exception);
                return fallback;
            }
        }
        Identifier id = id(key);
        client.getTextureManager().register(id, new DynamicTexture(id::toString, generate(source, variant)));
        RenderType type = RenderTypes.crumbling(id);
        TYPES.put(key, type);
        return type;
    }

    /** Bend concentric pixel rings without moving cracks away from their central origin.
     * The same radial bijection applies to every damage stage, preserving growth and coverage. */
    public static NativeImage generate(NativeImage source, int variant) {
        int size = source.getWidth();
        NativeImage result = new NativeImage(size, size, false);
        for (int y = 0; y < size; y++) for (int x = 0; x < size; x++) {
            int inset = Math.min(Math.min(x, y), Math.min(size - 1 - x, size - 1 - y));
            int edge = size - 1 - 2 * inset;
            int u = x;
            int v = y;
            if (edge > 1) {
                int radius = edge / 2;
                // At most one additional pixel of bend between adjacent rings.
                // No translation, edge wrapping, or detached off-center origins.
                int bend = switch (variant >>> 3) {
                    case 1 -> (radius + 1) / 2;
                    case 2 -> -(radius + 1) / 2;
                    case 3 -> radius <= size / 4 ? radius / 2 : size / 4 - radius / 2;
                    default -> 0;
                };
                int localX = x - inset;
                int localY = y - inset;
                int index = localY == 0 ? localX
                    : localX == edge ? edge + localY
                    : localY == edge ? 3 * edge - localX : 4 * edge - localY;
                index = Math.floorMod(index + bend, 4 * edge);
                if (index < edge) { u = inset + index; v = inset; }
                else if (index < 2 * edge) { u = inset + edge; v = inset + index - edge; }
                else if (index < 3 * edge) { u = inset + 3 * edge - index; v = inset + edge; }
                else { u = inset; v = inset + 4 * edge - index; }
            }
            if ((variant & 4) != 0) u = size - 1 - u;
            for (int rotation = 0; rotation < (variant & 3); rotation++) {
                int previous = u;
                u = size - 1 - v;
                v = previous;
            }
            result.setPixel(x, y, source.getPixel(u, v));
        }
        return result;
    }

    private static Identifier id(int key) {
        return Identifier.fromNamespaceAndPath("naturality", "dynamic/breaking/" + key);
    }

    /** Called on the render thread at atlas reload; the manager also owns shutdown. */
    public static void reset() {
        var manager = Minecraft.getInstance().getTextureManager();
        for (int key : TYPES.keySet()) manager.release(id(key));
        TYPES.clear();
        SOURCES.values().forEach(NativeImage::close);
        SOURCES.clear();
        FAILED.clear();
    }
}
