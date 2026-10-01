#version 330
#extension GL_ARB_separate_shader_objects : require
#include <minecraft:fog.glsl>
#include <minecraft:dynamictransforms.glsl>
#include <minecraft:oit.glsl>
#include <naturality:procedural_fire.glsl>
uniform sampler2D Sampler0;
layout(location = 0) in float sphericalVertexDistance;
layout(location = 1) in float cylindricalVertexDistance;
layout(location = 2) in vec2 texCoord0;
layout(location = 3) in vec4 vertexColor;
layout(location = 4) in vec3 firePosition;
layout(location = 5) flat in int fireKind;
#ifndef OIT_ALPHA_ONLY
layout(location = 0) out vec4 fragColor;
#endif
vec4 flameTexel(ivec2 pixel, int frame) {
    pixel = clamp(pixel, ivec2(0), ivec2(15));
    return texelFetch(NaturalityFirePalette,
        ivec2(fireKind * 16 + pixel.x, frame * 16 + pixel.y), 0);
}

// Reconstruct a continuous field before sampling the destination lattice. Nearest
// source sampling here would turn each original texel into a tall rectangle.
vec4 flameField(vec2 p, int frame) {
    ivec2 base = ivec2(floor(p));
    vec2 f = fract(p);
    vec4 a = flameTexel(base, frame), b = flameTexel(base + ivec2(1, 0), frame);
    vec4 c = flameTexel(base + ivec2(0, 1), frame), d = flameTexel(base + ivec2(1), frame);
    a.rgb *= a.a; b.rgb *= b.a; c.rgb *= c.a; d.rgb *= d.a;
    vec4 value = mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
    value.rgb /= max(value.a, 0.0001);
    return value;
}

void main() {
    vec2 resolution = naturality_fire_resolution(firePosition, texCoord0);
    vec2 cell = floor(clamp(texCoord0, 0.0, 0.999999) * resolution);
    vec2 point = (cell + 0.5) / resolution;
    ivec3 key = ivec3(round(vertexColor.rgb * 255.0));
    int tick = int(round(texelFetch(NaturalityFireHeat, ivec2(0), 0).b * 255.0));
    int frame = (tick + key.x + key.y * 7 + key.z * 13 + (fireKind % 2 == 0 ? 16 : 0)) % 32;
    int top = int(round(texelFetch(NaturalityFireHeat, ivec2(1 + fireKind * 32 + frame, 0), 0).b * 255.0));
    vec2 source = vec2(point.x * 16.0 - 0.5,
        float(top) + point.y * float(16 - top) - 0.5);
    // Additional detail lives on the destination grid, at equal X/Y density,
    // rather than inheriting the stretched source grid. Use animated vanilla
    // samples so it rises with the flame instead of producing random flicker.
    ivec2 detailPixel = ivec2(mod(cell.x + float(key.z), 16.0),
        4.0 + mod(cell.y, 8.0));
    vec4 detail = flameTexel(detailPixel, frame);
    float amount = clamp(resolution.y / float(16 - top) - 1.0, 0.0, 1.0);
    source.x += (detail.r - detail.b) * 0.35 * amount * sin(point.y * 3.14159265);
    vec4 color = flameField(source, frame);
    if (detail.a > 0.5 && color.a > 0.5)
        color.rgb = mix(color.rgb, detail.rgb, 0.18 * amount);
    // Rasterize both opacity and color once per square destination cell. No
    // filtering occurs between destination cells, preserving the pixel-art grid.
    color.a = step(0.5, color.a);
    color.rgb = floor(color.rgb * 255.0 + 0.5) / 255.0;
    color.a *= vertexColor.a * ColorModulator.a;
    if (color.a <= 0.0) discard;
#ifdef OIT_ALPHA_ONLY
    executeAlphaOnlyPhase(gl_FragCoord.z, color.a);
#else
    vec4 fogColor = FogColor;
#ifdef OIT_ACCUMULATE
    color = sampleColorForAccumulation(color);
    fogColor.rgb *= color.a;
#endif
    fragColor = apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance,
        FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, fogColor);
#endif
}

