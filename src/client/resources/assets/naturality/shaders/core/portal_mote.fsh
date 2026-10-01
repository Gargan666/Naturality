#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:fog.glsl>
#include <minecraft:dynamictransforms.glsl>
#include <minecraft:oit.glsl>
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
    if (abs(texCoord0.y) > 0.5) discard;
    vec4 color = texture(Sampler0, vec2(texCoord0.x, 0.5));
    color.rgb = naturality_portal_pulse(color.rgb, 1.0 - vertexColor.r);
    color.a *= vertexColor.a;
    color *= ColorModulator;
    // Preserve the complete arrival fade, rather than vanilla's 0.1 alpha cutoff.
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
