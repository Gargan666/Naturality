package naturality.client.items.golden;

import com.mojang.blaze3d.platform.NativeImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Optional;
import javax.imageio.ImageIO;
import naturality.items.golden.GoldenDye;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.ARGB;

/** Shader control masks derived from existing dye variants; never replaces supplied artwork. */
@org.jspecify.annotations.NullMarked
public final class GoldenVisuals {
    private GoldenVisuals() {}

    public static int maskedTint(int color) {
        // Ranked textures carry their own coverage. A whole-model marker would
        // reapply gold to pixels deliberately painted black in the editable mask.
        return (color & 0xFFFFFF) == GoldenDye.RGB ? (color & 0xFF000000) | 0xFFFFFF : color;
    }

    public static Identifier dyedTexture(Identifier texture, int color) {
        if ((color & 0xFFFFFF) != GoldenDye.RGB || !texture.getPath().startsWith("textures/")
                || texture.getPath().contains("golden")) return texture;
        return Identifier.fromNamespaceAndPath("naturality", "textures/golden_source/"
            + texture.getNamespace() + "/" + texture.getPath().substring("textures/".length()));
    }

    public static Optional<Resource> fallback(ResourceManager resources, Identifier id) {
        String path = id.getPath();
        if (!(id.getNamespace().equals("naturality") || id.getNamespace().equals("minecraft"))
                || !path.startsWith("textures/") || path.startsWith("textures/masks/")
                || !path.endsWith(".png") || !path.contains("golden")) return Optional.empty();
        String direct = "textures/golden_source/";
        boolean nativeLayer = path.startsWith(direct);
        String original = nativeLayer ? path.substring(direct.length()) : "";
        int separator = original.indexOf('/');
        if (nativeLayer && separator < 1) return Optional.empty();
        Identifier whiteId = nativeLayer
            ? Identifier.fromNamespaceAndPath(original.substring(0, separator), "textures/" + original.substring(separator + 1))
            : Identifier.withDefaultNamespace(path.replace("golden", "white"));
        var white = resources.getResource(whiteId);
        if (white.isEmpty()) return Optional.empty();
        var black = nativeLayer ? Optional.<Resource>empty()
            : resources.getResource(Identifier.withDefaultNamespace(path.replace("golden", "black")));
        String maskPath = switch (path) {
            case "textures/entity/equipment/happy_ghast_body/golden_harness.png" -> "textures/masks/golden_harness.png";
            case "textures/item/golden_harness.png" -> "textures/masks/golden_harness_item.png";
            default -> "textures/masks/golden/" + whiteId.getNamespace() + "/"
                + whiteId.getPath().substring("textures/".length());
        };
        var mask = resources.getResource(Identifier.fromNamespaceAndPath("naturality", maskPath));
        Resource source = white.get();
        return Optional.of(new Resource(source.source(), () -> {
            try (var input = source.open()) {
                var decoded = ImageIO.read(input);
                if (decoded == null) throw new IOException("Cannot decode dye source " + whiteId);
                // Indexed PNGs cannot represent new control colors in their original palette.
                var image = new java.awt.image.BufferedImage(decoded.getWidth(), decoded.getHeight(), java.awt.image.BufferedImage.TYPE_INT_ARGB);
                image.setRGB(0, 0, image.getWidth(), image.getHeight(),
                    decoded.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth()), 0, image.getWidth());
                java.awt.image.BufferedImage comparison = null;
                if (black.isPresent()) try (var stream = black.get().open()) { comparison = ImageIO.read(stream); }
                java.awt.image.BufferedImage coverage = null;
                if (mask.isPresent()) try (var stream = mask.get().open()) {
                    coverage = ImageIO.read(stream);
                    if (coverage == null) throw new IOException("Cannot decode golden effect mask " + maskPath);
                    if (coverage.getWidth() != image.getWidth() || coverage.getHeight() != image.getHeight())
                        throw new IOException("Golden effect mask must match the source texture dimensions: " + maskPath);
                }
                // The difference between native white/black variants is the authored dye mask.
                // Keep alpha, bed frames, candle wicks, buckles, faces and other undyed pixels intact.
                boolean wholeSurface = path.contains("stained_glass") || path.contains("concrete")
                    || path.contains("terracotta") || path.contains("shulker_box");
                boolean[] selected = new boolean[image.getWidth() * image.getHeight()];
                var shades = new java.util.TreeSet<Integer>();
                for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) {
                    int pixel = image.getRGB(x, y);
                    boolean dyed = wholeSurface || comparison == null || (pixel & 0xFFFFFF) !=
                        (comparison.getRGB(x * comparison.getWidth() / image.getWidth(), y * comparison.getHeight() / image.getHeight()) & 0xFFFFFF);
                    // The editable mask replaces automatic dye detection, including previously undyed straps.
                    // Black or transparent pixels retain the original white variant without the coating.
                    if (coverage != null) {
                        int maskPixel = coverage.getRGB(x, y);
                        dyed = ARGB.alpha(maskPixel) >= 128 && ARGB.red(maskPixel) >= 128;
                    }
                    if (dyed && ARGB.alpha(pixel) > 0) {
                        selected[y * image.getWidth() + x] = true;
                        shades.add(shade(pixel));
                    }
                }
                var ordered = new ArrayList<>(shades);
                for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) {
                    if (!selected[y * image.getWidth() + x]) continue;
                    int pixel = image.getRGB(x, y);
                    // Green stores relative shade rank, not absolute brightness. Ignore frequency:
                    // each distinct grayscale shade is one step in the source's own palette.
                    int index = java.util.Collections.binarySearch(ordered, shade(pixel));
                    int rank = ordered.size() <= 1 ? 255 : Math.round(255.0F * index / (ordered.size() - 1));
                    image.setRGB(x, y, (pixel & 0xFF000000) | (GoldenDye.RGB & 0xFF00FF) | (rank << 8));
                }
                var output = new ByteArrayOutputStream();
                if (!ImageIO.write(image, "png", output)) throw new IOException("PNG encoder unavailable");
                return new ByteArrayInputStream(output.toByteArray());
            }
        }, source::metadata));
    }

    private static int shade(int pixel) {
        // Integer luminance retains distinct source shades before eight-bit rank encoding.
        return 2126 * ARGB.red(pixel) + 7152 * ARGB.green(pixel) + 722 * ARGB.blue(pixel);
    }

    /** Compile the authored palette into the shader on each resource reload, without GPU bindings. */
    public static String palette(ResourceManager resources) throws IOException {
        var resource = resources.getResourceOrThrow(Identifier.parse("naturality:textures/misc/gold_glint.png"));
        var colors = new ArrayList<Integer>();
        try (var stream = resource.open(); var image = NativeImage.read(stream)) {
            // One entry per authored row, top (brightest) to bottom (darkest).
            // Repeated colors are intentional steps and must not be deduplicated.
            for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) {
                int color = image.getPixel(x, y);
                if (ARGB.alpha(color) > 0) { colors.add(color & 0xFFFFFF); break; }
            }
        }
        if (colors.isEmpty()) throw new IOException("gold_glint must contain visible palette pixels");
        var source = new StringBuilder("#ifndef NATURALITY_GOLD_PALETTE\n#define NATURALITY_GOLD_PALETTE\nconst bool naturalityGoldenEnabled = true;\n");
        source.append("const int naturalityGoldCount = ").append(colors.size()).append(";\nconst vec3 naturalityGoldPalette[")
            .append(colors.size()).append("] = vec3[](");
        boolean first = true;
        for (int color : colors) {
            if (!first) source.append(','); first = false;
            source.append(String.format(Locale.ROOT, "vec3(%.9f,%.9f,%.9f)", ARGB.red(color)/255.0, ARGB.green(color)/255.0, ARGB.blue(color)/255.0));
        }
        return source.append(");\n#endif\n").toString();
    }
}
