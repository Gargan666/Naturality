#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:fog.glsl>
#include <minecraft:dynamictransforms.glsl>
#include <minecraft:oit.glsl>
#include <naturality:lava_fog.glsl>
#include <naturality:golden_lines.glsl>

uniform sampler2D Sampler0;

layout(location = 0) in float sphericalVertexDistance;
layout(location = 1) in float cylindricalVertexDistance;
layout(location = 2) in vec2 texCoord0;
layout(location = 3) in vec4 vertexColor;

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
    vec4 color = texture(Sampler0, texCoord0);
    vec4 texel = texelFetch(Sampler0, ivec2(floor(texCoord0 * vec2(textureSize(Sampler0, 0)))), 0);
    if (naturality_golden_texel(texel.rgb)) {
        // Debris uses a fixed palette shade per source texel, with no animated bands.
        int shade = int(floor((1.0 - texel.g) * float(naturalityGoldCount - 1) + 0.5));
        color.rgb = naturalityGoldPalette[shade];
    }
    color *= vertexColor * ColorModulator;
    if (color.a < 0.1) {
        discard;
    }
    // Fully fogged particles must reveal the lava atmosphere instead of painting
    // dark fog over it. Apply the same alpha before every OIT phase; changing
    // only the final color leaves opaque coverage in the transparency buffers.
    if (FogColor.a >= 6.0) {
        float coverage = total_fog_value(sphericalVertexDistance, cylindricalVertexDistance,
            FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd);
        color.a *= 1.0 - naturality_lava_atmosphere_visibility(coverage);
        if (color.a <= 0.0) discard;
    }
    #ifdef OIT_ALPHA_ONLY
    executeAlphaOnlyPhase(gl_FragCoord.z, color.a);
    #else
    fragColor = calculateFinalColor(color);
    #endif
}
