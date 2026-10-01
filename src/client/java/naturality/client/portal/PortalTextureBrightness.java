package naturality.client.portal;

import com.mojang.blaze3d.platform.NativeImage;
import java.io.IOException;
import naturality.Naturality;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.sprite.SpriteId;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;

/** Active resource-pack luminance range, stable across animation frames, refreshed on atlas reload. */
public final class PortalTextureBrightness {
    private static @org.jspecify.annotations.Nullable TextureAtlasSprite currentSprite;
    private static int packedRange = 32767 << 16;

    public static int packedRange(Minecraft client) {
        var sprite = client.getAtlasManager().get(new SpriteId(naturality.client.AtlasLocations.BLOCKS,
            Identifier.withDefaultNamespace("block/nether_portal")));
        if (sprite == currentSprite) return packedRange;
        currentSprite = sprite;
        packedRange = 32767 << 16;
        try (var stream = client.getResourceManager().open(Identifier.withDefaultNamespace("textures/block/nether_portal.png"));
             var image = NativeImage.read(stream)) {
            float min = 1, max = 0;
            for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) {
                int pixel = image.getPixel(x, y);
                if (ARGB.alpha(pixel) == 0) continue;
                float luminance = (0.2126F * ARGB.red(pixel) + 0.7152F * ARGB.green(pixel) + 0.0722F * ARGB.blue(pixel)) / 255;
                min = Math.min(min, luminance);
                max = Math.max(max, luminance);
            }
            if (max >= min) {
                // Inward rounding ensures both texture endpoints clamp to exactly 0/1.
                int low = (int) Math.ceil(min * 32767), high = (int) Math.floor(max * 32767);
                packedRange = low | Math.max(low, high) << 16;
            }
        } catch (IOException e) {
            Naturality.LOGGER.warn("Could not sample portal reveal brightness range", e);
        }
        return packedRange;
    }
    private PortalTextureBrightness() { }
}
