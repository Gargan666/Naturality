#ifndef NATURALITY_DISTANCE_BANDS
#define NATURALITY_DISTANCE_BANDS

// Shared by gold and glass: unchanged nearby, gently widening beyond ten blocks.
// The cap keeps distant bands recognizable rather than growing without bound.
float naturality_band_distance_scale(vec3 relative) {
    return 1.0 + 0.25 * smoothstep(10.0, 50.0, length(relative));
}

#endif
