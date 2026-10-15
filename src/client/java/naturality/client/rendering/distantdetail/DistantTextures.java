package naturality.client.rendering.distantdetail;
import naturality.client.rendering.culling.FogCulling.OcclusionCulling;

import com.mojang.blaze3d.platform.NativeImage;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import naturality.client.blocks.snow.SnowTextureAtlas.SnowSpriteLookup;
import naturality.client.blocks.snow.SnowTextureAtlas.SnowSpritePixels;
import naturality.client.blocks.snow.SnowTextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import org.jspecify.annotations.Nullable;

/** Shared static sprite variants; the existing padded atlas owns their lifetime. */
public final class DistantTextures {
    private static volatile SnowSpriteLookup lookup = new SnowSpriteLookup(Map.of());
    private static final Map<Key, Variant> CACHE = new ConcurrentHashMap<>();
    private record Key(TextureAtlasSprite source, boolean solidLeaf, boolean average) {}
    public record Variant(TextureAtlasSprite sprite, boolean opaque) {}
    private DistantTextures() {}
    public static void loaded(Map<Identifier, TextureAtlasSprite> sprites) {
        CACHE.clear(); lookup = new SnowSpriteLookup(sprites); OcclusionCulling.reset();
    }
    public static @Nullable TextureAtlasSprite find(float u, float v) {
        var sprite = lookup.find(u, v);
        // Composite snow and our own variants share a reserved atlas tile, not
        // the original block's texture. Never flatten or reprocess that tile.
        return sprite != null && !sprite.contents().name().equals(SnowTextureAtlas.RESERVE) ? sprite : null;
    }
    public static Variant variant(TextureAtlasSprite source, boolean solidLeaf, boolean average) {
        if (source.isAnimated() || ((SnowSpritePixels)source.contents()).naturality$animation().isPresent()
                || SnowTextureAtlas.isComposite(source)
                || source.contents().name().equals(SnowTextureAtlas.RESERVE)) return new Variant(source, false);
        var key = new Key(source, solidLeaf, average);
        return CACHE.computeIfAbsent(key, DistantTextures::create);
    }
    private static Variant create(Key key) {
        var source = key.source();
        boolean solidLeaf = key.solidLeaf(), average = key.average();
        boolean[] opaque = {false};
        // Suppliers run inside the atlas lock, after verifying the resource epoch.
        java.util.function.Supplier<NativeImage> originalPixels = () -> {
            var image = ((SnowSpritePixels)source.contents()).naturality$image();
            int width = source.contents().width(), height = source.contents().height();
            int color = solidLeaf ? average(image, width, height) : 0;
            var pixels = new NativeImage(width, height, true);
            opaque[0] = true;
            for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
                int original = image.getPixel(x, y), alpha = ARGB.alpha(original);
                if (solidLeaf) {
                    pixels.setPixel(x, y, alpha == 0 ? color : original | 0xFF000000);
                } else {
                    opaque[0] &= alpha == 255;
                    pixels.setPixel(x, y, original);
                }
            }
            return pixels;
        };
        var sprite = average ? SnowTextureAtlas.lodSprite(key, source, originalPixels, () -> {
            var image = ((SnowSpritePixels)source.contents()).naturality$image();
            int width = source.contents().width(), height = source.contents().height();
            int color = average(image, width, height);
            var pixels = new NativeImage(width, height, true);
            for (int y = 0; y < height; y++) for (int x = 0; x < width; x++)
                pixels.setPixel(x, y, solidLeaf ? color : (color & 0xFFFFFF) | (image.getPixel(x, y) & 0xFF000000));
            return pixels;
        }, !solidLeaf) : SnowTextureAtlas.sprite(key, source, source, originalPixels);
        return new Variant(sprite, opaque[0] && sprite != source);
    }
    /** Alpha-weighted average excludes invisible RGB and retains biome tint separately. */
    public static int average(NativeImage image, int width, int height) {
        long r = 0, g = 0, b = 0, weight = 0;
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
            int pixel = image.getPixel(x, y), alpha = ARGB.alpha(pixel);
            r += (long)ARGB.red(pixel) * alpha; g += (long)ARGB.green(pixel) * alpha;
            b += (long)ARGB.blue(pixel) * alpha; weight += alpha;
        }
        return weight == 0 ? 0xFF000000 : ARGB.color(255, (int)(r / weight), (int)(g / weight), (int)(b / weight));
    }
}
