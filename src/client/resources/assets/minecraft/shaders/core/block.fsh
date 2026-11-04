#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:fog.glsl>
#include <minecraft:dynamictransforms.glsl>
#include <minecraft:oit.glsl>
#define NATURALITY_GOLD_FRAGMENT
#include <naturality:golden_lines.glsl>

uniform sampler2D Sampler0;

layout(location = 0) in float sphericalVertexDistance;
layout(location = 1) in float cylindricalVertexDistance;
layout(location = 2) in vec4 vertexColor;
layout(location = 3) in vec2 texCoord0;
layout(location = 4) in vec3 naturalityGoldenPosition;
layout(location = 5) in vec3 naturalityGoldenView;

#ifndef OIT_ALPHA_ONLY
layout(location = 0) out vec4 fragColor;
#endif

vec4 calculateFinalColor(vec4 color) {
    #ifdef OIT_ACCUMULATE
    color = sampleColorForAccumulation(color);
    vec4 fogColor = vec4(FogColor.rgb * color.a, FogColor.a);
    #else
    vec4 fogColor = FogColor;
    #endif

    return apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, fogColor);
}

void main() {
    vec3 normal = cross(dFdx(naturalityGoldenPosition), dFdy(naturalityGoldenPosition));
    vec2 grid = naturality_gold_surface_grid(naturalityGoldenPosition, normal);
    vec4 view = naturality_gold_pixel_view(grid, naturalityGoldenView);
    vec4 texel = texelFetch(Sampler0, ivec2(floor(texCoord0 * vec2(textureSize(Sampler0, 0)))), 0);
    vec4 color = texture(Sampler0, texCoord0);
    if (naturality_golden_texel(texel.rgb)) {
        color.rgb = naturality_gold_overlay(vec3(texel.g), grid, view);
    }
    color *= vertexColor * ColorModulator;
    #ifdef ALPHA_CUTOUT
    if (color.a < ALPHA_CUTOUT) {
        discard;
    }
    #endif

    #ifdef OIT_ALPHA_ONLY
    executeAlphaOnlyPhase(gl_FragCoord.z, color.a);
    #else
    fragColor = calculateFinalColor(color);
    #endif
}
