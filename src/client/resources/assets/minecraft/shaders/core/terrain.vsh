#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:fog.glsl>
#include <minecraft:globals.glsl>
#include <minecraft:projection.glsl>
#include <minecraft:sample_lightmap.glsl>
#include <minecraft:terrainglobals.glsl>
#include <naturality:menu_geometry.glsl>
#include <naturality:fluid_uniforms.glsl>
#include <naturality:fire_uniforms.glsl>
#include <naturality:glass_uniforms.glsl>
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

uniform sampler2D Sampler2;

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
layout(location = 19) out vec2 naturalityFireShape;
layout(location = 15) flat out int naturalitySubmerged;
#include <naturality:foliage_wind.glsl>

layout(location = 16) flat out int naturalityGlassKind;
layout(location = 17) out vec3 naturalityGlassRelative;
layout(location = 18) flat out vec3 naturalityGlassForward;

void main() {
    bool fallingFluidTail = (UV2.x & 4096) != 0;
    ivec2 lightCoords = UV2 & ivec2(4095);
    vec3 pos = Position + (ChunkPosition - CameraBlockPos) + CameraOffset;
    naturalityGlassRelative = pos;
    naturalityGlassForward = transpose(mat3(ModelViewMat)) * vec3(0.0, 0.0, -1.0);
    naturalityGlassKind = -1;
    for (int i = 0; i < 34; i++) {
        vec4 b = GlassBounds[i];
        if (b.x >= 0.0 && all(greaterThanEqual(UV0, b.xy)) && all(lessThanEqual(UV0, b.zw))) {
            naturalityGlassKind = i;
            break;
        }
    }
    int foliageTag = int(round(Color.a * 255.0));
    int windTag = foliageTag;
    bool snowLeafOverlay = foliageTag >= 224 && foliageTag <= 231;
    bool anchoredCrop = foliageTag >= 120 && foliageTag <= 127;
    bool snowSurface = foliageTag == 192 || (foliageTag >= 128 && foliageTag <= 159 && !anchoredCrop);
    if (anchoredCrop) foliageTag = 64;
    else if (foliageTag >= 128 && foliageTag <= 159) foliageTag += 32;
    bool anchoredPlant = foliageTag >= 64 && foliageTag <= 99;
    bool attachedLeaf = foliageTag >= 160 && foliageTag <= 191;
    bool supportedVine = foliageTag >= 102 && foliageTag <= 117;
    bool connectedPlant = foliageTag == 100 || foliageTag == 101 || supportedVine;
    bool windFoliage = foliageTag == 118 || foliageTag <= 63 || connectedPlant || anchoredPlant || attachedLeaf || snowLeafOverlay
        || (foliageTag >= 193 && foliageTag <= 240);
    gl_Position = ProjMat * ModelViewMat * vec4(pos, 1.0);

    sphericalVertexDistance = fog_spherical_distance(pos);
    cylindricalVertexDistance = fog_cylindrical_distance(pos);
    // Keep glass light gating identical in color and alpha-only OIT stages.
    vertexColor = sample_lightmap(Sampler2, lightCoords);
    texCoord0 = UV0;
    // Section-local positions retain precision; section origins are multiples of 16 blocks.
    naturalityBlockPosition = Position;
    naturalityLightLevels = (vec2(lightCoords) + 8.0) / 256.0;
    naturalityShade = Color;
    if (windFoliage || snowSurface || windTag == 243 || windTag == 244) naturalityShade.a = 1.0;
    int waterTag = int(round(Color.a * 255.0));
    naturalitySubmerged = waterTag >= 246 && waterTag <= 254 ? waterTag - 245 : 0;
    if (waterTag == 244) naturalitySubmerged = 9;
    if (naturalitySubmerged != 0) naturalityShade.a = 1.0;
    naturalitySectionOrigin = ChunkPosition;
    naturalityFluidKind = -1;
    bool terrainFluidSprite = false;
    naturalityFluidUV = vec2(0.0);
    naturalityFlow = vec3(0.0);
    naturalityFireKind = -1;
    naturalityFireUV = vec2(0.0);
    naturalityFireSeed = Color.rgb;
    naturalityFireShape = Color.gb * (255.0 / 16.0) - 4.0;
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
            terrainFluidSprite = true;
            windFoliage = false;
            snowSurface = false;
            // Negative means water material; positive values remain submerged
            // terrain heights. Classify once per primitive, not from interpolated
            // fragment UVs, which can round just outside an atlas edge.
            naturalitySubmerged = i < 2 ? -1 : 0;
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

    if (fallingFluidTail) naturalityFlow.z = -1.0;
    float windDetail = naturality_wind_detail(pos);
    if (windFoliage && windDetail > 0.0) {
        vec2 offset = naturality_wind_offset(Position, ChunkPosition, windTag) * windDetail;
        pos.xz += offset;
        gl_Position = ProjMat * ModelViewMat * vec4(pos, 1.0);
    }
    if (!terrainFluidSprite && naturalityFireKind < 0)
        pos += naturality_brush_offset(Position, ChunkPosition, windTag);
    pos.xz += naturality_menu_extension(Position + ChunkPosition);
    pos.y -= naturality_menu_wall_drop(Position + ChunkPosition);
    gl_Position = ProjMat * ModelViewMat * vec4(pos, 1.0);
    if (snowSurface) gl_Position.z += gl_Position.w * 0.000001;
    const float chunkFullyVisibleRange = 16.0;
    float dist = length(pos);
    chunkVisibility = mix(1.0, ChunkVisibility, clamp((dist - chunkFullyVisibleRange) / chunkFullyVisibleRange, 0.0, 1.0));
}




