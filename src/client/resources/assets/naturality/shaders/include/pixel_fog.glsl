#ifndef NATURALITY_PIXEL_FOG_GLSL
#define NATURALITY_PIXEL_FOG_GLSL

// Pixel size is in framebuffer pixels, independent of GUI scale.
#include <naturality:pixel_fog_settings.glsl>

float naturality_fog_cell_distance(float distanceValue) {
    vec2 center = (floor(gl_FragCoord.xy / NATURALITY_FOG_PIXEL_SIZE) + 0.5)
        * NATURALITY_FOG_PIXEL_SIZE;
    vec2 offset = center - gl_FragCoord.xy;
    // Evaluate only this surface's distance: no depth reads or fog bleeding across silhouettes.
    return max(0.0, distanceValue + dot(vec2(dFdx(distanceValue), dFdy(distanceValue)), offset));
}

float naturality_fog_threshold() {
    const float bayer[16] = float[16](
        0.0, 8.0, 2.0, 10.0,
        12.0, 4.0, 14.0, 6.0,
        3.0, 11.0, 1.0, 9.0,
        15.0, 7.0, 13.0, 5.0);
    ivec2 cell = ivec2(floor(gl_FragCoord.xy / NATURALITY_FOG_PIXEL_SIZE));
    return (bayer[(cell.x % 4) + (cell.y % 4) * 4] + 0.5) / 16.0;
}

float naturality_fog_density(float amount) {
    float threshold = naturality_fog_threshold();
    // A fixed ordered pattern fills whole squares as fog thickens. No animated noise.
    return clamp(floor(amount * NATURALITY_FOG_STEPS + threshold)
        / NATURALITY_FOG_STEPS, 0.0, 1.0);
}

#endif
