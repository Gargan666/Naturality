#version 330
#extension GL_ARB_separate_shader_objects : require
#include <minecraft:dynamictransforms.glsl>
uniform sampler2D Sampler0;
layout(location=0) in vec2 texCoord0;
layout(location=1) in vec4 vertexColor;
layout(location=0) out vec4 fragColor;
void main() {
    vec4 color=texture(Sampler0,texCoord0);
    float t=clamp(vertexColor.r,0.0,1.0);
    float ei=t<=0.0?0.0:(t>=1.0?1.0:exp2(10.0*(t-1.0)));
    float eo=t<=0.0?0.0:(t>=1.0?1.0:1.0-exp2(-10.0*t));
    float l=dot(color.rgb,vec3(0.2126,0.7152,0.0722));
    float span=vertexColor.b-vertexColor.g;
    float b=span>0.000001?clamp((l-vertexColor.g)/span,0.0,1.0):0.5;
    color.a=b<=0.5?mix(ei,t,b*2.0):mix(t,eo,(b-0.5)*2.0);
    if(color.a<=0.0) discard;
    fragColor=color*ColorModulator;
}
