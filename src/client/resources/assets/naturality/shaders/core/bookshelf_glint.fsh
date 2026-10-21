#version 330
#extension GL_ARB_separate_shader_objects : require
#include <minecraft:fog.glsl>
#include <minecraft:globals.glsl>
#include <minecraft:dynamictransforms.glsl>
#include <minecraft:oit.glsl>
#include <naturality:pixel_glint.glsl>

uniform sampler2D Sampler0;
layout(location = 0) in float sphericalVertexDistance;
layout(location = 1) in float cylindricalVertexDistance;
layout(location = 2) in vec2 texCoord0;
#ifndef OIT_ALPHA_ONLY
layout(location = 0) out vec4 fragColor;
#endif

void main() {
    vec4 color = naturality_pixel_glint_at(Sampler0, texCoord0) * ColorModulator;
    if (color.a < 0.1) discard;
    float fade = (1.0 - total_fog_value(sphericalVertexDistance, cylindricalVertexDistance,
        FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd)) * GlintAlpha;
    color.rgb *= fade;
#ifdef OIT_ALPHA_ONLY
    executeAlphaOnlyPhase(gl_FragCoord.z, color.a);
#elif defined(OIT_ACCUMULATE)
    // GLINT uses SRC_COLOR + ONE: square the source RGB before additive accumulation.
    // Alpha one avoids applying a second opacity factor to that contribution.
    fragColor = sampleColorForAccumulation(vec4(color.rgb * color.rgb, 1.0));
#else
    fragColor = color;
#endif
}
