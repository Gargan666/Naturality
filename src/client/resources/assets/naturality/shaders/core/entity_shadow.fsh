#version 330
#extension GL_ARB_separate_shader_objects : require
#include <minecraft:fog.glsl>
#include <minecraft:dynamictransforms.glsl>
#include <minecraft:oit.glsl>

uniform sampler2D Sampler0;
layout(location = 0) in float sphericalVertexDistance;
layout(location = 1) in float cylindricalVertexDistance;
layout(location = 2) in vec4 vertexColor;
layout(location = 3) in vec2 texCoord0;
layout(location = 4) flat in vec2 gridPhase;

#ifndef OIT_ALPHA_ONLY
layout(location = 0) out vec4 fragColor;
#endif

void main() {
    vec2 size = vec2(textureSize(Sampler0, 0));
    // Each world cell stays square and constant. Its sample follows the continuous
    // entity position, so edge coverage fades between cells instead of snapping.
    vec2 cell = floor(texCoord0 * size - gridPhase) + 0.5 + gridPhase;
    vec4 color = texture(Sampler0, clamp(cell / size, 0.0, 1.0));
    color *= vertexColor * ColorModulator;
    #ifdef OIT_ALPHA_ONLY
    executeAlphaOnlyPhase(gl_FragCoord.z, color.a);
    #else
        #ifdef OIT_ACCUMULATE
        color = sampleColorForAccumulation(color);
        vec4 fogColor = vec4(FogColor.rgb * color.a, FogColor.a);
        #else
        vec4 fogColor = FogColor;
        #endif
    fragColor = apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance,
        FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, fogColor);
    #endif
}
