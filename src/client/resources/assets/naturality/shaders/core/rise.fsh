#version 330
#extension GL_ARB_separate_shader_objects : require
#include <minecraft:fog.glsl>
#include <minecraft:dynamictransforms.glsl>
#include <minecraft:oit.glsl>
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
    // A uniformly colored square, squashed across its velocity axis into a rectangle.
    if(abs(texCoord0.y)>0.5)discard;
    ivec2 size=textureSize(Sampler0,0);
    float column=fract(texCoord0.x)*float(size.x);
    int first=int(floor(column)),second=(first+1)%size.x;
    float blend=fract(column);blend=blend*blend*(3.0-2.0*blend);
    float row=clamp(brightnessRange.x,0.0,1.0)*float(size.y-1);
    int low=int(floor(row)),high=min(low+1,size.y-1);
    float level=fract(row);level=level*level*(3.0-2.0*level);
    vec4 a=mix(texelFetch(Sampler0,ivec2(first,low),0),texelFetch(Sampler0,ivec2(second,low),0),blend);
    vec4 b=mix(texelFetch(Sampler0,ivec2(first,high),0),texelFetch(Sampler0,ivec2(second,high),0),blend);
    vec4 color=mix(a,b,level);
    color.a*=vertexColor.a;
    color*=ColorModulator;
    if(color.a<=0.0)discard;
#ifdef OIT_ALPHA_ONLY
    executeAlphaOnlyPhase(gl_FragCoord.z,color.a);
#else
    vec4 fogColor=FogColor;
#ifdef OIT_ACCUMULATE
    color=sampleColorForAccumulation(color);fogColor.rgb*=color.a;
#endif
    fragColor=apply_fog(color,sphericalVertexDistance,cylindricalVertexDistance,
        FogEnvironmentalStart,FogEnvironmentalEnd,FogRenderDistanceStart,FogRenderDistanceEnd,fogColor);
#endif
}