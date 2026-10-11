#version 330
#extension GL_ARB_separate_shader_objects : require
#include <minecraft:texture_sampling.glsl>
#include <naturality:cutout_pixels.glsl>
uniform sampler2D Sampler0;
layout(location=0) in vec2 texCoord;
layout(location=0) out vec4 fragColor;
void main() {
    vec2 uv=texCoord-vec2(0.15/257.0,0.0);
#ifdef DISTANT
    uv.x*=256.0;
#endif
    vec4 color=sampleNearest(Sampler0,uv,vec2(0.5,1.0));
#ifdef FIX_EDGE
    color=naturality_cutout_pixels(Sampler0,uv,vec2(2.0,1.0),color);
#endif
    if(color.a<0.5)discard;
    fragColor=vec4(color.rgb*0.4,1.0);
}
