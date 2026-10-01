package naturality.client.fire;

/** Shared ember-to-firelight color ramp used by lava splash highlights. */
public final class FirePalette {
    private static final int[] COLORS={
        0xFF1A0803,0xFF4B1003,0xFF8F1D00,0xFFD83400,
        0xFFF96300,0xFFFF9A08,0xFFFFD12E,0xFFFFF5BA
    };
    private FirePalette() {}
    public static int color(float brightness) {
        float position=Math.clamp(brightness,0,1)*(COLORS.length-1);
        int index=Math.min(COLORS.length-2,(int)position);
        float t=position-index;
        int a=COLORS[index],b=COLORS[index+1];
        int r=Math.round(((a>>>16)&255)*(1-t)+((b>>>16)&255)*t);
        int g=Math.round(((a>>>8)&255)*(1-t)+((b>>>8)&255)*t);
        int blue=Math.round((a&255)*(1-t)+(b&255)*t);
        return 0xFF000000|r<<16|g<<8|blue;
    }
}
