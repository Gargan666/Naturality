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
    // Geometry already assigns an integer gradient row to each cell. Interpolating
    // its constant UV can land just below that integer; flooring it caused speckled
    // row/opacity changes across a coplanar cell. Round back to the assigned row.
    float progress=clamp(round(texCoord0.x*16.0)/max(1.0,round(texCoord0.y*16.0)),0.0,1.0);
    vec3 palette=texture(Sampler0,vec2((floor(progress*255.0)+0.5)/256.0,0.5)).rgb;
    vec4 color=vec4(palette,vertexColor.a*(1.0-progress))*ColorModulator;
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
