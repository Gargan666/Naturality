package naturality.client.particle;

import java.util.Arrays;
import java.util.Comparator;

/** A light-to-dark lookup table containing only colors present in the portal. */
public final class PortalGlowPalette {
    public static final int SIZE = 256;
    private PortalGlowPalette() { }

    public static int[] create(int[] pixels) {
        Integer[] colors = Arrays.stream(pixels).filter(pixel -> (pixel >>> 24) != 0)
            .map(pixel -> pixel | 0xFF000000).distinct().boxed()
            .sorted(Comparator.<Integer>comparingDouble(PortalGlowPalette::luminance)
                .reversed().thenComparingInt(Integer::intValue)).toArray(Integer[]::new);
        int[] result = new int[SIZE];
        if (colors.length == 0) return result;
        for (int i = 0; i < SIZE; i++) {
            result[i] = colors[(int) Math.round(i * (colors.length - 1.0) / (SIZE - 1))];
        }
        return result;
    }

    private static double luminance(int color) {
        return 0.2126 * linear((color >>> 16) & 255)
            + 0.7152 * linear((color >>> 8) & 255) + 0.0722 * linear(color & 255);
    }

    private static double linear(int channel) {
        double value = channel / 255.0;
        return value <= 0.04045 ? value / 12.92 : Math.pow((value + 0.055) / 1.055, 2.4);
    }
}
