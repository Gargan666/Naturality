#version 330
#extension GL_ARB_separate_shader_objects : require
#include <minecraft:fog.glsl>
#include <minecraft:dynamictransforms.glsl>
#include <minecraft:oit.glsl>
#include <minecraft:globals.glsl>
uniform sampler2D Sampler0;
layout(location = 0) in float sphericalVertexDistance;
layout(location = 1) in float cylindricalVertexDistance;
layout(location = 2) in vec2 texCoord0;
layout(location = 3) in vec4 vertexColor;
#ifdef NATURALITY_PIXEL_GLOW
layout(location = 4) flat in vec2 brightnessRange;
#endif
#ifndef OIT_ALPHA_ONLY
layout(location = 0) out vec4 fragColor;
#endif
void main() {
    float life = vertexColor.r;
    if (life <= 0.0) discard;
    float slice = floor(texCoord0.x * 16.0);
#ifdef NATURALITY_PIXEL_GLOW
    float pixelSize = 1.0 / max(round(brightnessRange.x * 32767.0), 1.0);
    slice = floor(texCoord0.x / pixelSize);
#endif
    float wave = sin(6.28318530718 * (slice / 24.0 - GameTime * 200.0));
    float reach = (0.65 + wave * (1.5 / 16.0)) * life;
    float distance = texCoord0.y;
#ifdef NATURALITY_PIXEL_GLOW
    reach = ceil(reach * 0.35 / pixelSize) * pixelSize;
    distance = floor(distance / pixelSize) * pixelSize;
#endif
    if (distance >= reach) discard;
    float progress = clamp(distance / max(reach, 0.00001), 0.0, 1.0);
    float index = smoothstep(0.0, 1.0, progress) * 255.0;
    int low = int(floor(index));
    vec4 palette = mix(texelFetch(Sampler0, ivec2(low, 0), 0),
        texelFetch(Sampler0, ivec2(min(low + 1, 255), 0), 0), fract(index));
    float maximum = max(palette.r, max(palette.g, palette.b));
    float minimum = min(palette.r, min(palette.g, palette.b));
    // Preserve the sampled hue/value, with HSV saturation 100% -> 0%.
    vec3 saturated = maximum > minimum
        ? (palette.rgb - minimum) * maximum / (maximum - minimum) : palette.rgb;
    vec3 tint = mix(vec3(maximum), saturated, life);
    vec4 color = vec4(tint, palette.a * vertexColor.a * (1.0 - smoothstep(0.0, 1.0, progress))) * ColorModulator;
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
