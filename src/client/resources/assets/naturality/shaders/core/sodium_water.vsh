#version 330
#extension GL_ARB_separate_shader_objects : require

// Decode Sodium's own compressed vertices; retain its region draws and sorting.
#include <sodium:globals.glsl>
#include <sodium:chunk_vertex.glsl>
#include <naturality:fluid_uniforms.glsl>
#include <naturality:water.glsl>
#include <naturality:foliage_wind.glsl>

uniform isamplerBuffer u_SectionTimeInfo;
#ifdef VULKAN
layout(push_constant) uniform PC {
    vec3 u_RegionOffset;
    int u_CurrentTime;
    uint u_RegionID;
};
#else
uniform vec3 u_RegionOffset;
uniform int u_CurrentTime;
uniform uint u_RegionID;
#endif
#ifndef OIT_ALPHA_ONLY
uniform sampler2D u_LightTex;
#endif

layout(location=0) out float sphericalVertexDistance;
layout(location=1) out float cylindricalVertexDistance;
layout(location=2) out vec4 vertexColor;
layout(location=3) out vec2 texCoord0;
layout(location=4) out float chunkVisibility;
layout(location=5) out vec3 naturalityBlockPosition;
layout(location=6) out vec4 naturalityShade;
layout(location=7) flat out ivec3 naturalitySectionOrigin;
layout(location=8) flat out int naturalityFluidKind;
layout(location=9) out vec2 naturalityFluidUV;
layout(location=10) out vec3 naturalityFlow;
layout(location=11) out vec2 naturalityLightLevels;
layout(location=12) flat out int naturalityFireKind;
layout(location=13) out vec2 naturalityFireUV;
layout(location=14) flat out vec3 naturalityFireSeed;
layout(location=15) flat out int naturalitySubmerged;

void main() {
    _vert_init();
    uvec3 section = (uvec3(_draw_id) >> uvec3(5u,0u,2u)) & uvec3(7u,3u,7u);
    vec3 translation = u_RegionOffset + vec3(section)*16.0;
    vec3 position = translation + _vert_position;
    gl_Position = u_ProjectionMatrix*u_ModelViewMatrix*vec4(position,1.0);
    sphericalVertexDistance=length(position);
    cylindricalVertexDistance=max(length(position.xz),abs(position.y));
    int arrival=texelFetch(u_SectionTimeInfo,int(u_RegionID*256u+_draw_id)).r;
    chunkVisibility=arrival<0?1.0:clamp(float(u_CurrentTime-arrival)*u_FadePeriodInv,0.0,1.0);
    texCoord0=_vert_tex_diffuse_coord+_vert_tex_diffuse_coord_bias*u_TexCoordShrink;
    naturalityBlockPosition=_vert_position;
    // Keep large integer map origins out of floating-point calculations.
    naturalitySectionOrigin=ivec3(round(translation+WaterCamera.xyz))+ivec3(WaterMap.x,0,WaterMap.y);
    naturalityShade=vec4(_vert_color.rgb,1.0);
    naturalityLightLevels=_vert_tex_light_coord;
    #ifndef OIT_ALPHA_ONLY
    vertexColor=texture(u_LightTex,_vert_tex_light_coord);
    #else
    vertexColor=vec4(1.0);
    #endif
    naturalityFluidKind=-1;
    naturalityFluidUV=vec2(0.0);
    naturalityFlow=vec3(0.0); // Shared shader reconstructs flow from UV derivatives.
    bool fluidSprite=false;
    for(int i=0;i<4;i++) {
        vec4 b=NaturalityFluidBounds[i];
        if(b.x>=0.0 && all(greaterThanEqual(texCoord0,b.xy)) && all(lessThanEqual(texCoord0,b.zw))) {
            fluidSprite=true;
            naturalityFluidKind=(NaturalityFluidInfo.w & (1<<i))!=0?i:-1;
            naturalityFluidUV=(texCoord0-b.xy)/(b.zw-b.xy);
        }
    }
    naturalityFireKind=-1;
    naturalityFireUV=vec2(0.0);
    naturalityFireSeed=vec3(0.0);
    naturalitySubmerged=0;
    int windTag=fluidSprite?255:int(round(_vert_color.a*255.0));
    position.xz+=naturality_wind_offset(_vert_position,naturalitySectionOrigin,windTag);
    gl_Position=u_ProjectionMatrix*u_ModelViewMatrix*vec4(position,1.0);
    if(windTag==192 || (windTag>=128 && windTag<=159))
        gl_Position.z+=gl_Position.w*0.000001;
}
