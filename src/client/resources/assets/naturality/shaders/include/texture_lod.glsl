#include <minecraft:texture_sampling.glsl>

// Region tests use the original UVs, preserving alpha/wind/lighting metadata.
float naturality_texture_lod_weight(vec2 uv, vec4 bounds, float distanceToCamera) {
    bool paired = bounds.x >= 0.0 && all(greaterThanEqual(uv, bounds.xy))
        && all(lessThan(uv, bounds.zw));
    return paired ? smoothstep(160.0, 224.0, distanceToCamera) : 0.0;
}
vec2 naturality_texture_average_uv(vec2 uv, vec4 bounds) {
    return uv + vec2(bounds.z - bounds.x, 0.0);
}

// Vanilla RGSS/nearest filtering with derivatives supplied before the distance
// branch. Evaluating them inside that branch is undefined along the fade edge.
vec4 naturality_texture_detail(sampler2D source, vec2 uv, vec2 pixelSize, vec2 du, vec2 dv, bool rgss) {
    vec2 footprint = sqrt(du * du + dv * dv);
    vec4 nearestColor = sampleNearest(source, uv, pixelSize, du, dv, footprint);
    if (!rgss) return nearestColor;
    float minPixelSize = min(pixelSize.x, pixelSize.y);
    float blend = smoothstep(minPixelSize, minPixelSize * 2.0, max(footprint.x, footprint.y));
    float duLength = length(du), dvLength = length(dv);
    float effectiveDerivative = sqrt(min(duLength, dvLength) * max(duLength, dvLength));
    float mip = max(0.0, log2(effectiveDerivative / minPixelSize));
    const vec2 offsets[4] = vec2[](vec2(0.125, 0.375), vec2(-0.125, -0.375),
        vec2(0.375, -0.125), vec2(-0.375, 0.125));
    vec4 low = vec4(0.0), high = vec4(0.0);
    for (int i = 0; i < 4; i++) {
        vec2 tap = uv + offsets[i] * pixelSize;
        low += textureLod(source, tap, floor(mip));
        high += textureLod(source, tap, floor(mip) + 1.0);
    }
    return mix(nearestColor, mix(low * 0.25, high * 0.25, fract(mip)), blend);
}
