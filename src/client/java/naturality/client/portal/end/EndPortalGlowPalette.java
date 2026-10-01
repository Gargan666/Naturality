package naturality.client.portal.end;

import com.mojang.blaze3d.platform.NativeImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.regex.Pattern;
import naturality.Naturality;
import naturality.client.particle.PortalGlowPalette;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;

/** Bright, fully opaque texels from the nearest star layer, before attenuation. */
public final class EndPortalGlowPalette {
    public static final Identifier TEXTURE = Naturality.id("dynamic/end_portal_glow_palette");
    private static @org.jspecify.annotations.Nullable Object loadedModels;
    private static final Pattern TINT = Pattern.compile("vec3\\(\\s*([0-9.]+)\\s*,\\s*([0-9.]+)\\s*,\\s*([0-9.]+)\\s*\\)");

    public static void prepare(Minecraft client) {
        Object models = client.getModelManager().getBlockStateModelSet();
        if (models == loadedModels) return;
        var colors = new HashSet<Integer>();
        try (var texture = client.getResourceManager().open(Identifier.withDefaultNamespace("textures/entity/end_portal/end_portal.png"));
             var image = NativeImage.read(texture);
             var source = client.getResourceManager().open(Naturality.id("shaders/include/end_portal_colors.glsl"))) {
            var matcher = TINT.matcher(new String(source.readAllBytes(), StandardCharsets.UTF_8));
            var tints = new ArrayList<float[]>();
            // Entry 0 is the nearest visible star plane. Deeper planes must not
            // introduce unrelated hues into the border's continuous gradient.
            if (matcher.find()) {
                float r = Float.parseFloat(matcher.group(1)), g = Float.parseFloat(matcher.group(2)), b = Float.parseFloat(matcher.group(3));
                float max = Math.max(r, Math.max(g, b));
                if (max > 0) tints.add(new float[]{r / max, g / max, b / max});
            }
            var texels = new HashSet<Integer>();
            for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) {
                int pixel = image.getPixel(x, y);
                if (ARGB.alpha(pixel) == 255) texels.add(pixel);
            }
            for (int pixel : texels) for (float[] tint : tints) {
                int r = Math.round(ARGB.red(pixel) * tint[0]);
                int g = Math.round(ARGB.green(pixel) * tint[1]);
                int b = Math.round(ARGB.blue(pixel) * tint[2]);
                // Reject dark source colors instead of manufacturing brightened ones.
                if (0.2126 * r + 0.7152 * g + 0.0722 * b >= 0.55 * 255)
                    colors.add(ARGB.color(255, r, g, b));
            }
        } catch (IOException | NumberFormatException exception) {
            Naturality.LOGGER.warn("Could not sample bright End portal star colors", exception);
        }
        int[] palette = PortalGlowPalette.create(colors.stream().mapToInt(Integer::intValue).toArray());
        var image = new NativeImage(PortalGlowPalette.SIZE, 1, false);
        for (int i = 0; i < palette.length; i++) image.setPixel(i, 0, palette[i]);
        client.getTextureManager().register(TEXTURE, new DynamicTexture(() -> "Naturality bright End star palette", image));
        loadedModels = models;
    }

    private EndPortalGlowPalette() { }
}
