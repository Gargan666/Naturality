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
layout(location = 4) flat in vec2 brightnessRange;
#ifndef OIT_ALPHA_ONLY
layout(location = 0) out vec4 fragColor;
#endif
void main() {
    vec4 color = texture(Sampler0, texCoord0);
    float t = clamp(vertexColor.r, 0.0, 1.0);
    float expoIn = t <= 0.0 ? 0.0 : (t >= 1.0 ? 1.0 : exp2(10.0 * (t - 1.0)));
    float expoOut = t <= 0.0 ? 0.0 : (t >= 1.0 ? 1.0 : 1.0 - exp2(-10.0 * t));
    float luminance = dot(color.rgb, vec3(0.2126, 0.7152, 0.0722));
    float span = brightnessRange.y - brightnessRange.x;
    float brightness = span > 0.000001 ? clamp((luminance - brightnessRange.x) / span, 0.0, 1.0) : 0.5;
    float reveal = brightness <= 0.5
        ? mix(expoIn, t, brightness * 2.0)
        : mix(t, expoOut, (brightness - 0.5) * 2.0);
    // The ready surface is opaque; opening still reveals each texel independently.
    color.a = reveal;
    color.rgb = naturality_portal_pulse(color.rgb, vertexColor.g);
    color *= ColorModulator;
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
