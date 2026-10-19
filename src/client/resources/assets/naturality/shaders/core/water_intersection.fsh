#version 330
#extension GL_ARB_separate_shader_objects : require
#include <minecraft:fog.glsl>
#include <minecraft:dynamictransforms.glsl>
#include <minecraft:oit.glsl>
uniform sampler2D Sampler0;
#ifdef NATURALITY_WATER_SURFACE_EFFECT
uniform sampler2D NaturalityWaterMask;
uniform sampler2D NaturalityWaterMaskDepth;
uniform sampler2D NaturalityWaterOpaqueDepth;
#endif
layout(location=0) in float sphericalVertexDistance;
layout(location=1) in float cylindricalVertexDistance;
layout(location=2) in vec2 texCoord0;
layout(location=3) in vec4 vertexColor;
#ifndef OIT_ALPHA_ONLY
layout(location=0) out vec4 fragColor;
#endif
void main(){
    #ifdef NATURALITY_WATER_SURFACE_EFFECT
    ivec2 pixel = clamp(ivec2(gl_FragCoord.xy), ivec2(0), textureSize(NaturalityWaterMask, 0) - 1);
    // A hull adds private depth where it prevented the actual water surface
    // from drawing. Ordinary opaque occlusion and chunk dithering do not add
    // private depth. Only suppress surface effects behind that posed hull.
    float hull = texelFetch(NaturalityWaterMaskDepth, pixel, 0).r;
    float opaque = texelFetch(NaturalityWaterOpaqueDepth, pixel, 0).r;
    if (hull > opaque + 0.000001 && gl_FragCoord.z <= hull + 0.000001
            && texelFetch(NaturalityWaterMask, pixel, 0).r <= 0.0) discard;
    #endif
    vec4 color=vertexColor*ColorModulator;
    if(color.a<=0.0) discard;
#ifdef OIT_ALPHA_ONLY
    executeAlphaOnlyPhase(gl_FragCoord.z,color.a);
#else
    vec4 fog=FogColor;
#ifdef OIT_ACCUMULATE
    color=sampleColorForAccumulation(color);fog.rgb*=color.a;
#endif
    fragColor=apply_fog(color,sphericalVertexDistance,cylindricalVertexDistance,FogEnvironmentalStart,FogEnvironmentalEnd,FogRenderDistanceStart,FogRenderDistanceEnd,fog);
#endif
}

