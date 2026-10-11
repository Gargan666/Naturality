#version 330
#extension GL_ARB_separate_shader_objects : require
#include <minecraft:projection.glsl>
#include <minecraft:dynamictransforms.glsl>
uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
layout(std140) uniform HeatVolumes { vec4 HeatInfo; vec4 HeatBox[128]; vec4 HeatAnchor; };
layout(location=0) in vec2 texCoord;
layout(location=1) flat in mat4 inverseProjection;
layout(location=0) out vec4 fragColor;
vec3 position(vec2 uv, float depth) {
#ifndef RENDERPEARL_DEPTH_IS_ZERO_TO_ONE
    depth = depth * 2.0 - 1.0;
#endif
    vec4 p = inverseProjection * vec4(uv*2.0-1.0, depth, 1.0);
    return transpose(mat3(ModelViewMat)) * (p.xyz/p.w);
}
struct HeatSample { float strength; vec3 worldPosition; float sourceTop; };
HeatSample heat(vec2 uv, float sufficient) {
    vec3 p = position(uv, max(texture(Sampler1,uv).r, 0.000001));
    float distance = length(p);
    vec3 ray = p / max(distance,0.00001);
    vec3 reciprocal = sign(ray + vec3(1e-10)) / max(abs(ray),vec3(1e-7));
    float amount = 0.0;
    vec3 worldPosition = vec3(0.0);
    float selectedTop = 0.0;
    for (int i=0; i<int(HeatInfo.x); i++) {
        // Spatial/depth tapers cannot exceed one. Preserve the first winning
        // volume on ties, just as the original full search did.
        if (HeatBox[i*2].w <= amount) continue;
        vec3 lo=HeatBox[i*2].xyz, hi=HeatBox[i*2+1].xyz;
        vec3 a=lo*reciprocal, b=hi*reciprocal;
        vec3 nearPoint=min(a,b), farPoint=max(a,b);
        float entry=max(0.0,max(nearPoint.x,max(nearPoint.y,nearPoint.z)));
        float end=min(distance,min(farPoint.x,min(farPoint.y,farPoint.z)));
        if (end <= entry) continue;
        vec3 middle=ray*((entry+end)*0.5);
        vec3 edge=min(middle-lo,hi-middle);
        bool fire=HeatBox[i*2+1].w > 0.5;
        float spatial=fire
            ? (1.0-smoothstep(0.0,hi.y-lo.y,middle.y-lo.y))
                * smoothstep(0.0,0.15,min(edge.x,edge.z))
            : smoothstep(0.0,1.0,min(edge.x,min(edge.y,edge.z)));
        float weight=spatial * smoothstep(0.0,0.5,end-entry) * HeatBox[i*2].w;
        if (weight > amount) {
            amount=weight;
            worldPosition=middle+vec3(HeatAnchor.x,HeatInfo.w,HeatAnchor.y);
            // Retain the current taller-row lattice's source-top anchor.
            selectedTop=(fire ? lo.y : hi.y-2.125)+HeatInfo.w;
            if (amount >= sufficient) break;
        }
    }
    return HeatSample(amount,worldPosition,selectedTop);
}
float patch_wave(vec2 cell,float row) {
    vec2 seed=mod(cell,256.0);
    float hash=fract(sin(dot(seed,vec2(127.1,311.7)))*43758.5453);
    return sin(row*0.55+hash*6.2831853-HeatInfo.y*(2.1+hash*1.4));
}
float local_wave(HeatSample region) {
    vec3 worldPosition=region.worldPosition;
    vec2 lattice=worldPosition.xz/2.0;
    vec2 cell=floor(lattice);
    vec2 blend=fract(lattice);
    blend=blend*blend*(3.0-2.0*blend);
    float row=floor(worldPosition.y*16.0);
    if (worldPosition.y>=region.sourceTop) {
        float height=max(0.0,worldPosition.y-region.sourceTop);
        float band=0.0, bottom=0.0;
        // Keep the two closest 1/4-block rows, easing outer rows toward 5/16.
        for (int i=0; i<12; i++) {
            float rowHeight=mix(0.25,0.3125,smoothstep(0.5,2.0,bottom));
            band=float(i);
            if (height<bottom+rowHeight) break;
            bottom+=rowHeight;
        }
        row=region.sourceTop*16.0+band;
    }
    return mix(mix(patch_wave(cell,row),patch_wave(cell+vec2(1,0),row),blend.x),
        mix(patch_wave(cell+vec2(0,1),row),patch_wave(cell+vec2(1,1),row),blend.x),blend.y);
}
void main() {
    HeatSample region=heat(texCoord,1.0);
    float mask=region.strength;
    if (mask == 0.0) {
        fragColor=texture(Sampler0,texCoord);
        return;
    }
    vec2 size=vec2(textureSize(Sampler0,0));
    float wave=local_wave(region);
    float power=3.0*HeatInfo.z;
    vec2 sampleUv=texCoord+vec2(power*wave*mask/size.x,0.0);
    // Only min(mask,destination) is needed; stop once destination reaches mask.
    float destination=heat(sampleUv,mask).strength;
    sampleUv=texCoord+vec2(power*wave*min(mask,destination)/size.x,0.0);
    fragColor=texture(Sampler0,clamp(sampleUv,vec2(0.5)/size,1.0-vec2(0.5)/size));
}
