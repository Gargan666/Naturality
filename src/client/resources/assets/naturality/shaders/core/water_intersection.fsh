#version 330
#extension GL_ARB_separate_shader_objects : require
#include <minecraft:fog.glsl>
#include <minecraft:dynamictransforms.glsl>
#include <minecraft:oit.glsl>
uniform sampler2D Sampler0;
layout(location=0) in float sphericalVertexDistance;
layout(location=1) in float cylindricalVertexDistance;
layout(location=2) in vec2 texCoord0;
layout(location=3) in vec4 vertexColor;
#ifndef OIT_ALPHA_ONLY
layout(location=0) out vec4 fragColor;
#endif
void main(){
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

