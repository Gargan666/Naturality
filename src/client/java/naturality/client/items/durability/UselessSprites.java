package naturality.client.items.durability;

import com.mojang.blaze3d.platform.NativeImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.resources.metadata.animation.AnimationMetadataSection;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.client.resources.metadata.texture.TextureMetadataSection;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.metadata.MetadataSectionType.WithValue;
import net.minecraft.util.ARGB;

/** Resource-pack-aware sprite production; atlas ownership also handles reload disposal. */
@org.jspecify.annotations.NullMarked
public final class UselessSprites {
    private UselessSprites() {}

    public record Source(NativeImage image, Optional<AnimationMetadataSection> animation,
            List<WithValue<?>> metadata, Optional<TextureMetadataSection> texture) {}

    public interface Pixels {
        Source naturality$source();
    }

    public static Identifier id(Identifier original) {
        return Identifier.fromNamespaceAndPath("naturality", "useless/" + original.getNamespace() + "/" + original.getPath());
    }

    public static List<SpriteContents> expand(List<SpriteContents> originals, Identifier atlas) {
        if (!atlas.getPath().equals("textures/atlas/items.png")
                && !atlas.getPath().equals("textures/atlas/blocks.png")) return originals;
        List<SpriteContents> sprites = new ArrayList<>(originals);
        for (var original : originals) {
            // The coating allocator reserves a large empty tile, never an item texture.
            if (original.name().equals(naturality.client.blocks.snow.SnowTextureAtlas.RESERVE)
                    || original.name().getPath().startsWith("useless/")) continue;
            Source source = ((Pixels)original).naturality$source();
            NativeImage image = recolor(source.image(), original.width(), original.height(), original.name().hashCode());
            sprites.add(new SpriteContents(id(original.name()), new FrameSize(original.width(), original.height()),
                image, source.animation(), source.metadata(), source.texture()));
        }
        return sprites;
    }

    public static NativeImage recolor(NativeImage source, int frameWidth, int frameHeight, long seed) {
        NativeImage result = new NativeImage(source.getWidth(), source.getHeight(), true);
        result.copyFrom(source);
        int darkest = 0, minimum = Integer.MAX_VALUE;
        for (int y = 0; y < source.getHeight(); y++) for (int x = 0; x < source.getWidth(); x++) {
            int color = source.getPixel(x, y);
            int brightness = 2126 * ARGB.red(color) + 7152 * ARGB.green(color) + 722 * ARGB.blue(color);
            if (ARGB.alpha(color) > 0 && brightness < minimum) {
                minimum = brightness;
                darkest = color & 0xFFFFFF;
            }
        }
        // Select an exact rounded fifth per frame, without replacement; stable on reload.
        int[] visible = new int[frameWidth * frameHeight];
        boolean[] removed = new boolean[visible.length];
        for (int top = 0; top < source.getHeight(); top += frameHeight) {
            for (int left = 0; left < source.getWidth(); left += frameWidth) {
                Arrays.fill(removed, false);
                int count = 0;
                for (int y = 0; y < frameHeight; y++) for (int x = 0; x < frameWidth; x++) {
                    if (ARGB.alpha(source.getPixel(left + x, top + y)) > 0) visible[count++] = y * frameWidth + x;
                }
                Random random = new Random(seed);
                int selected = Math.round(count * 0.2F);
                for (int i = 0; i < selected; i++) {
                    int index = i + random.nextInt(count - i);
                    int pixel = visible[index];
                    visible[index] = visible[i];
                    visible[i] = pixel;
                    removed[pixel] = true;
                }
                // Read the completed mask so borders never paint over adjacent holes.
                for (int y = 0; y < frameHeight; y++) for (int x = 0; x < frameWidth; x++) {
                    int pixel = y * frameWidth + x;
                    int color = source.getPixel(left + x, top + y);
                    if (removed[pixel]) {
                        result.setPixel(left + x, top + y, 0);
                    } else if (ARGB.alpha(color) > 0 && (
                            (x > 0 && removed[pixel - 1])
                            || (x + 1 < frameWidth && removed[pixel + 1])
                            || (y > 0 && removed[pixel - frameWidth])
                            || (y + 1 < frameHeight && removed[pixel + frameWidth]))) {
                        result.setPixel(left + x, top + y, (color & 0xFF000000) | darkest);
                    }
                }
            }
        }
        return result;
    }
}
