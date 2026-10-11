#version 330
#extension GL_ARB_separate_shader_objects : require
#include <minecraft:projection.glsl>
#include <minecraft:dynamictransforms.glsl>
uniform sampler2D Sampler0;
layout(std140) uniform HeatVolumes { vec4 HeatInfo; vec4 HeatBox[128]; vec4 HeatAnchor; };
layout(location=0) out vec2 texCoord;
layout(location=1) flat out mat4 inverseProjection;

void main() {
    // The same inverse serves both depth queries for every covered pixel.
    inverseProjection=inverse(ProjMat);
    vec2 lower=vec2(1.0), upper=vec2(-1.0);
    for (int i=0; i<int(HeatInfo.x); i++) {
        vec2 boxLower=vec2(1e30), boxUpper=vec2(-1e30);
        bool inFront=false, behind=false;
        for (int corner=0; corner<8; corner++) {
            vec3 point=mix(HeatBox[i*2].xyz,HeatBox[i*2+1].xyz,
                vec3(corner&1,(corner>>1)&1,(corner>>2)&1));
            // Match the inverse rotation used by the depth reconstruction.
            // Use the actual draw projection, including view bob/distortion.
            vec4 clip=ProjMat*vec4(mat3(ModelViewMat)*point,1.0);
            if (clip.w <= 0.0001) behind=true;
            else {
                inFront=true;
                vec2 ndc=clip.xy/clip.w;
                boxLower=min(boxLower,ndc);
                boxUpper=max(boxUpper,ndc);
            }
        }
        if (!inFront) continue;
        // A box crossing the eye plane can surround the camera: retain the
        // entire viewport rather than risk clipping the visible heat halo.
        if (behind) { lower=vec2(-1.0); upper=vec2(1.0); break; }
        lower=min(lower,boxLower);
        upper=max(upper,boxUpper);
    }
    // One pixel of numerical slack. Offscreen boxes collapse to an empty quad.
    vec2 margin=2.0/vec2(textureSize(Sampler0,0));
    lower=clamp(lower-margin,vec2(-1.0),vec2(1.0));
    upper=clamp(upper+margin,lower,vec2(1.0));
    const vec2 corners[6]=vec2[6](vec2(0,0),vec2(1,0),vec2(0,1),
        vec2(0,1),vec2(1,0),vec2(1,1));
    vec2 ndc=mix(lower,upper,corners[gl_VertexIndex]);
    texCoord=ndc*0.5+0.5;
    gl_Position=vec4(ndc,0.0,1.0);
}
