#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:fog.glsl>
#include <minecraft:globals.glsl>
#include <minecraft:projection.glsl>
#include <minecraft:sample_lightmap.glsl>
#include <minecraft:terrainglobals.glsl>
#include <naturality:fluid_uniforms.glsl>
#include <naturality:fire_uniforms.glsl>
#include <naturality:water.glsl>
#ifndef MULTIDRAW_TERRAIN
    #include <minecraft:chunksection.glsl>
#endif

layout(location = 0) in vec3 Position;
layout(location = 1) in vec4 Color;
layout(location = 2) in vec2 UV0;
layout(location = 3) in ivec2 UV2;
#ifdef MULTIDRAW_TERRAIN
layout(location = 4) in ivec3 ChunkPosition;
layout(location = 5) in float ChunkVisibility;
#endif

#ifndef OIT_ALPHA_ONLY
uniform sampler2D Sampler2;
#endif

layout(location = 0) out float sphericalVertexDistance;
layout(location = 1) out float cylindricalVertexDistance;
layout(location = 2) out vec4 vertexColor;
layout(location = 3) out vec2 texCoord0;
layout(location = 4) out float chunkVisibility;
layout(location = 5) out vec3 naturalityBlockPosition;
layout(location = 6) out vec4 naturalityShade;
layout(location = 7) flat out ivec3 naturalitySectionOrigin;
layout(location = 8) flat out int naturalityFluidKind;
layout(location = 9) out vec2 naturalityFluidUV;
layout(location = 10) out vec3 naturalityFlow;
layout(location = 11) out vec2 naturalityLightLevels;
layout(location = 12) flat out int naturalityFireKind;
layout(location = 13) out vec2 naturalityFireUV;
layout(location = 14) flat out vec3 naturalityFireSeed;
layout(location = 15) flat out int naturalitySubmerged;
#include <naturality:foliage_wind.glsl>

void main() {
    vec3 pos = Position + (ChunkPosition - CameraBlockPos) + CameraOffset;
    int foliageTag = int(round(Color.a * 255.0));
    bool snowSurface = foliageTag == 192 || (foliageTag >= 128 && foliageTag <= 159);
    if (foliageTag >= 128 && foliageTag <= 159) foliageTag += 32;
    bool anchoredPlant = foliageTag >= 64 && foliageTag <= 99;
    bool attachedLeaf = foliageTag >= 160 && foliageTag <= 191;
    bool supportedVine = foliageTag >= 102 && foliageTag <= 117;
    bool connectedPlant = foliageTag == 100 || foliageTag == 101 || supportedVine;
    bool windFoliage = connectedPlant || anchoredPlant || attachedLeaf || (foliageTag >= 201 && foliageTag <= 240);
    gl_Position = ProjMat * ModelViewMat * vec4(pos, 1.0);

    sphericalVertexDistance = fog_spherical_distance(pos);
    cylindricalVertexDistance = fog_cylindrical_distance(pos);
    #ifndef OIT_ALPHA_ONLY
    vertexColor = sample_lightmap(Sampler2, UV2);
    #else
    vertexColor = vec4(1.0);
    #endif
    texCoord0 = UV0;
    // Section-local positions retain precision; section origins are multiples of 16 blocks.
    naturalityBlockPosition = Position;
    naturalityLightLevels = (vec2(UV2) + 8.0) / 256.0;
    naturalityShade = Color;
    if (windFoliage || snowSurface) naturalityShade.a = 1.0;
    int waterTag = int(round(Color.a * 255.0));
    naturalitySubmerged = waterTag >= 246 && waterTag <= 254 ? waterTag - 245 : 0;
    if (naturalitySubmerged != 0) naturalityShade.a = 1.0;
    naturalitySectionOrigin = ChunkPosition;
    naturalityFluidKind = -1;
    naturalityFluidUV = vec2(0.0);
    naturalityFlow = vec3(0.0);
    naturalityFireKind = -1;
    naturalityFireUV = vec2(0.0);
    naturalityFireSeed = Color.rgb;
    for (int i = 0; i < 4; i++) {
        vec4 b = NaturalityFireBounds[i];
        if (b.x >= 0.0 && all(greaterThanEqual(UV0, b.xy)) && all(lessThanEqual(UV0, b.zw))) {
            naturalityFireKind = i;
            windFoliage = false;
            snowSurface = false;
            naturalitySubmerged = 0;
            naturalityFireUV = (UV0 - b.xy) / (b.zw - b.xy);
            naturalityShade = vec4(1.0);
        }
    }
    for (int i = 0; i < 4; i++) {
        vec4 b = NaturalityFluidBounds[i];
        if (b.x >= 0.0 && all(greaterThanEqual(UV0, b.xy)) && all(lessThanEqual(UV0, b.zw))) {
            naturalityFluidKind = (NaturalityFluidInfo.w & (1 << i)) != 0 ? i : -1;
            windFoliage = false;
            snowSurface = false;
            naturalitySubmerged = 0; // Fluid alpha carries flow, not the terrain tag.
            int flowCode = int(round(Color.a * 255.0));
            if (flowCode < 255) {
                float angle = (float(flowCode) + 0.5) * (6.28318530718 / 254.0) - 3.14159265359;
                naturalityFlow = vec3(flowCode == 254 ? vec2(0.0) : vec2(cos(angle), sin(angle)), 1.0);
                naturalityShade.a = 1.0;
            }
            // Remove the large atlas offset BEFORE interpolation/derivatives.
            naturalityFluidUV = (UV0 - b.xy) / (b.zw - b.xy);
        }
    }

    if (windFoliage) {
        vec2 offset = naturality_wind_offset(Position, ChunkPosition, foliageTag);
        pos.xz += offset;
        gl_Position = ProjMat * ModelViewMat * vec4(pos, 1.0);
    }
    if (snowSurface) gl_Position.z += gl_Position.w * 0.000001;
    const float chunkFullyVisibleRange = 16.0;
    float dist = length(pos);
    chunkVisibility = mix(1.0, ChunkVisibility, clamp((dist - chunkFullyVisibleRange) / chunkFullyVisibleRange, 0.0, 1.0));
}




