#ifndef NATURALITY_PIXEL_LIGHTING_GLSL
#define NATURALITY_PIXEL_LIGHTING_GLSL
#include <naturality:pixel_lighting_settings.glsl>

// Evaluate an interpolated lighting field at a block-grid cell center.
// AO and the sky/block lightmap use the same grid, before multiplication.
// Texture sampling, alpha and atmospheric fog stay outside this calculation.
vec3 naturality_pixel_lighting(vec3 position, vec3 shade) {
    if (!NATURALITY_LIGHTING_ENABLED) return shade;
    vec3 dx = dFdx(position);
    vec3 dy = dFdy(position);
    vec3 sx = dFdx(shade);
    vec3 sy = dFdy(shade);
    vec3 normal = abs(cross(dx, dy));

    // Project onto the face's dominant plane, including slabs and stairs.
    vec2 p;
    vec2 px;
    vec2 py;
    if (normal.x >= normal.y && normal.x >= normal.z) {
        p = position.yz; px = dx.yz; py = dy.yz;
    } else if (normal.y >= normal.z) {
        p = position.xz; px = dx.xz; py = dy.xz;
    } else {
        p = position.xy; px = dx.xy; py = dy.xy;
    }
    float determinant = px.x * py.y - px.y * py.x;
    if (abs(determinant) < 1e-12) {
        return shade;
    }
    vec2 offset = (floor(p * 16.0) + 0.5) / 16.0 - p;
    // Solve in surface space, so perspective and camera angle cannot move the grid.
    vec2 weights = vec2(offset.x * py.y - offset.y * py.x,
                        px.x * offset.y - px.y * offset.x) / determinant;
    return clamp(shade + sx * weights.x + sy * weights.y, 0.0, 1.0);
}



vec3 naturality_step_brightness(vec3 lighting, float steps) {
    if (!NATURALITY_LIGHTING_ENABLED) return lighting;
    float brightness = max(lighting.r, max(lighting.g, lighting.b));
    if (brightness <= 0.0) {
        return vec3(0.0);
    }
    // Keep full-bright emission exactly full bright.
    if (brightness >= 1.0) {
        return lighting;
    }
    float stepped = floor(brightness * steps + 0.5) / steps;
    // A common scale preserves RGB ratios and never clips a channel.
    return lighting * (stepped / brightness);
}

vec3 naturality_adaptive_light(vec3 lighting) {
    if (!NATURALITY_LIGHTING_ENABLED) return lighting;
    float brightness = max(lighting.r, max(lighting.g, lighting.b));
    if (brightness <= 0.0) return vec3(0.0);
    if (brightness >= 1.0) return lighting;
    if (abs(NATURALITY_LIGHT_RANGE - 1.0) < 0.000001) {
        return naturality_step_brightness(lighting, NATURALITY_LIGHT_BANDS);
    }
    // Quantize in logarithmic space, then invert it. Fixed ordered levels avoid
    // brightness reversals caused by rounding with a continuously varying step.
    float curve = NATURALITY_LIGHT_RANGE - 1.0;
    float band = floor(log(1.0 + curve * brightness) / log(NATURALITY_LIGHT_RANGE)
        * NATURALITY_LIGHT_BANDS + 0.5);
    float stepped = (pow(NATURALITY_LIGHT_RANGE, band / NATURALITY_LIGHT_BANDS) - 1.0) / curve;
    return lighting * (stepped / brightness);
}

vec3 naturality_shadow_saturation(vec3 color, vec3 lighting) {
    if (!NATURALITY_LIGHTING_ENABLED) return color;
    float brightness = clamp(max(lighting.r, max(lighting.g, lighting.b)), 0.0, 1.0);
    float darkness = 1.0 - smoothstep(NATURALITY_SATURATION_FULL, NATURALITY_SATURATION_START, brightness);
    float luminance = dot(color, vec3(0.2126, 0.7152, 0.0722));
    // Boost distance from grey, retaining neutral greys and full-bright colors.
    return clamp(mix(vec3(luminance), color,
        1.0 + NATURALITY_DARK_SATURATION * darkness), 0.0, 1.0);
}

#endif
