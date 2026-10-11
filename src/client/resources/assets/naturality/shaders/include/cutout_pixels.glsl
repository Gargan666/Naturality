#ifndef NATURALITY_CUTOUT_PIXELS_GLSL
#define NATURALITY_CUTOUT_PIXELS_GLSL

// Magnified cutout boundaries must not inherit RGB from invisible neighbor
// texels. Keep distant mip/coverage filtering and authored partial alpha.
vec4 naturality_cutout_pixels(sampler2D source, vec2 uv, vec2 atlasSize, vec4 filtered) {
    vec2 footprint=max(abs(dFdx(uv)),abs(dFdy(uv)))*atlasSize;
    if (filtered.a > 0.0 && filtered.a < 1.0 && max(footprint.x,footprint.y) < 1.0) {
        ivec2 pixel=ivec2(clamp(floor(uv*atlasSize),vec2(0.0),atlasSize-1.0));
        vec4 center=texelFetch(source,pixel,0);
        if (center.a == 0.0 || center.a == 1.0) return center;
    }
    return filtered;
}
#endif
