package naturality.client.particle;

import com.mojang.blaze3d.platform.NativeImage;
import java.io.IOException;
import java.io.InputStream;
import naturality.Naturality;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;

/** Opacity and nearest-filtered palette of the active portal PNG. */
public record PortalGlowAppearance(float opacity) {
    public static final Identifier PALETTE_TEXTURE = Naturality.id("dynamic/portal_glow_palette");
    public static PortalGlowAppearance load(Minecraft client) {
        Identifier texture = Identifier.withDefaultNamespace("textures/block/nether_portal.png");
        try (InputStream stream = client.getResourceManager().open(texture);
             NativeImage image = NativeImage.read(stream)) {
            int[] pixels = new int[image.getWidth() * image.getHeight()];
            double weight = 0;
            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    int pixel = image.getPixel(x, y);
                    int alpha = ARGB.alpha(pixel);
                    pixels[y * image.getWidth() + x] = pixel;
                    weight += alpha;
                }
            }
            upload(client, PortalGlowPalette.create(pixels));
            return new PortalGlowAppearance((float) (weight / pixels.length / 255));
        } catch (IOException exception) {
            Naturality.LOGGER.warn("Could not sample nether portal palette; using fallback", exception);
        }
        upload(client, PortalGlowPalette.create(new int[] {0xFFCC66FF, 0xFF661ACC, 0xFF220044}));
        return new PortalGlowAppearance(0.75F);
    }

    private static void upload(Minecraft client, int[] colors) {
        NativeImage image = new NativeImage(PortalGlowPalette.SIZE, 1, false);
        for (int i = 0; i < colors.length; i++) image.setPixel(i, 0, colors[i]);
        // TextureManager closes the previous texture at this ID on resource reload.
        client.getTextureManager().register(PALETTE_TEXTURE,
            new DynamicTexture(() -> "Naturality portal glow palette", image));
    }
}
