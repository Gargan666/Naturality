#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:fog.glsl>
#include <minecraft:dynamictransforms.glsl>
#include <minecraft:oit.glsl>
#include <minecraft:globals.glsl>
#include <naturality:portal_pulse.glsl>

uniform sampler2D Sampler0;

layout(location = 0) in float sphericalVertexDistance;
layout(location = 1) in float cylindricalVertexDistance;
layout(location = 2) in vec2 texCoord0;
layout(location = 3) in vec4 vertexColor;

#ifndef OIT_ALPHA_ONLY
layout(location = 0) out vec4 fragColor;
#endif

void main() {
    // Both coordinates use fixed 1/16-block cells; geometry never moves.
    float slice = floor(texCoord0.x * float(GLOW_SLICES));
    float row = floor(texCoord0.y * float(GLOW_SLICES));
    // 24-pixel wavelength, six-second period. GameTime wraps after one game day;
    // 200 whole cycles/day keeps that wrap continuous.
    float phase = 6.28318530718 * (slice / 24.0 - GameTime * 200.0);
    float pulse = clamp(1.0 - vertexColor.r, 0.0, 1.0);
    float reachScale = 1.0 + 0.5 * pulse;
    float reach = GLOW_PORTAL_SURFACE
        + (GLOW_BASE_REACH + GLOW_WAVE_AMPLITUDE * sin(phase) - GLOW_PORTAL_SURFACE) * reachScale;
    float reachPixels = floor(reach * float(GLOW_SLICES));
    float rootPixels = GLOW_PORTAL_SURFACE * float(GLOW_SLICES);
    // Start at the portal surface, not inside its translucent slab.
    if (row < rootPixels || row >= reachPixels) discard;
    // Last visible row is darkest; the following row is fully clear.
    float progress = clamp((row - rootPixels) / max(1.0, reachPixels - 1.0 - rootPixels), 0.0, 1.0);
    float eased = smoothstep(0.0, 1.0, progress);
    float paletteIndex = floor(eased * 255.0 + 0.5);
    vec3 paletteColor = texture(Sampler0, vec2((paletteIndex + 0.5) / 256.0, 0.5)).rgb;
    float falloff = 1.0 - smoothstep(rootPixels, reachPixels, row);
    vec4 color = vec4(naturality_portal_pulse(paletteColor, pulse), vertexColor.a) * ColorModulator;
    color.a *= falloff;
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
        FogEnvironmentalStart, FogEnvironmentalEnd,
        FogRenderDistanceStart, FogRenderDistanceEnd, fogColor);
#endif
}
