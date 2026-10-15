package naturality.client.fog;

import net.minecraft.client.renderer.fog.FogData;

public final class DistanceFogSpan {
    private DistanceFogSpan() {}
    /** Preserve short-distance visibility, progressively broaden longer horizons. */
    public static void apply(FogData fog, int chunks) {
        float t = Math.clamp((chunks - 8) / 24F, 0, 1);
        t = t * t * (3 - 2 * t);
        float fraction = .10F + .20F * t;
        float span = Math.max(4, fog.renderDistanceEnd * fraction);
        fog.renderDistanceStart = Math.min(fog.renderDistanceStart, Math.max(0, fog.renderDistanceEnd - span));
    }
}
