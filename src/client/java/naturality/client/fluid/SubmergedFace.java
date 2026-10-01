package naturality.client.fluid;

/** Terrain tint-alpha tags: 254 fully wet; 246..253 encode partial water heights. */
public final class SubmergedFace {
    private SubmergedFace() {}

    public static int tag(float minY, float maxY, int fluidY, float height) {
        float surface = fluidY + height;
        if (minY > surface + 0.0001F || maxY < fluidY) return 255;
        if (maxY <= surface + 0.0001F) return 254;
        // Partial vanilla block faces stay within the fluid's block. The shader
        // tests their fractional Y against the exact 1/9-block fluid height.
        // Do not tag oversized/custom faces whose block origin is ambiguous.
        if (minY < fluidY || maxY > fluidY + 1.0001F) return 255;
        return 245 + Math.clamp(Math.round(height * 9.0F), 1, 8);
    }
}
