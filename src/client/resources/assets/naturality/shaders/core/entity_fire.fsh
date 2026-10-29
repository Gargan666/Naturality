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
layout(location = 6) flat in vec2 fireResolution;
#ifndef OIT_ALPHA_ONLY
layout(location = 0) out vec4 fragColor;
#endif
void main() {
    vec2 resolution = fireResolution;
    vec2 cell = floor(clamp(texCoord0, 0.0, 0.999999) * resolution);
    vec2 point = (cell + 0.5) / resolution;
    vec4 color = naturality_fire_color(fireKind, point, resolution, vertexColor.rgb);
    // Rasterize both opacity and color once per square destination cell. No
    // filtering occurs between destination cells, preserving the pixel-art grid.
    color.a = step(0.5, color.a);
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

