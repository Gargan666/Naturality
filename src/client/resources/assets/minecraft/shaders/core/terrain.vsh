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
layout(std140) uniform NaturalityWind { vec4 NaturalityWindValue; };

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
        // Sample on the world's 1/16-block grid, before camera subtraction.
        // Section origins are whole blocks; wrapping X/Z preserves the periodic
        // phase while retaining precision far from spawn (including negatives).
        vec3 worldPos = floor(Position * 16.0 + 0.5) / 16.0
            + vec3(ChunkPosition.x % 4096, ChunkPosition.y, ChunkPosition.z % 4096);
        if (attachedLeaf) {
            int anchor = foliageTag - 160;
            vec3 cellOffset = vec3(anchor & 1, anchor / 4, (anchor / 2) & 1);
            worldPos = floor(Position + 0.002) - cellOffset + 0.5
                + vec3(ChunkPosition.x % 4096, ChunkPosition.y, ChunkPosition.z % 4096);
        }
        float plantHeight = 0.0;
        if (anchoredPlant) {
            int anchor = foliageTag - 64;
            vec3 cellOffset = vec3(anchor % 3 - 1, anchor / 9 - 1, (anchor / 3) % 3 - 1);
            vec3 base = floor(Position + 0.002) - cellOffset;
            plantHeight = Position.y - base.y;
            worldPos = base + 0.5 + vec3(ChunkPosition.x % 4096, ChunkPosition.y, ChunkPosition.z % 4096);
        }
        float phase = dot(worldPos.xz, vec2(111.0, 85.0)) * (6.28318530718 / 4096.0) + worldPos.y * 0.08;
        float wave = sin(NaturalityWindValue.z * 1.256637 + phase) * 0.7
            + sin(NaturalityWindValue.z * 3.141593 + phase * 2.0) * 0.3;
        float weight = foliageTag == 101 ? 0.0 : connectedPlant ? 0.22 : (attachedLeaf || foliageTag == 240) ? 0.12 : anchoredPlant ? 0.22 : 0.22 * float(foliageTag - 201) / 19.0;
        vec2 offset = NaturalityWindValue.xy * wave * weight;
        // Faster, smaller directional gusts layer onto the broad bend. Related
        // world-space phases vary their timing without turning motion into a circle.
        float shake = smoothstep(0.80, 1.0, NaturalityWindValue.w);
        float shakePhase = NaturalityWindValue.z * 16.755161 + phase * 2.3;
        float finePhase = NaturalityWindValue.z * 23.876104 + phase * 3.7
            + dot(worldPos.xz, vec2(-0.23, 0.31));
        float detailPhase = NaturalityWindValue.z * 29.530971 + phase * 1.3
            + dot(worldPos.xz, vec2(0.37, 0.19));
        float shakeAmount = sin(shakePhase) * 0.075
            + sin(finePhase) * 0.030 + sin(detailPhase) * 0.015;
        float windMagnitude = length(NaturalityWindValue.xy);
        vec2 windDirection = windMagnitude > 0.0001 ? NaturalityWindValue.xy / windMagnitude : vec2(0.0);
        offset += windDirection * shakeAmount * shake * (weight / 0.22);
        // Whole texture-pixel steps along world X/Z, never screen-space pixels.
        // Quantize displacement only, preserving model offsets and anchored roots.
        offset = sign(offset) * floor(abs(offset) * 16.0 + 0.5) / 16.0;
        // Quantize one common tip displacement, then interpolate the anchored
        // bend. Subdivided overlay faces now lie exactly on the original plane.
        if (anchoredPlant) offset *= plantHeight;
        // Attachment directions point into supporting blocks. Clip only motion
        // toward those faces, after pixel quantization, including corner vines.
        if (supportedVine) {
            int supports = foliageTag - 102;
            if ((supports & 1) != 0) offset.x = max(offset.x, 0.0);
            if ((supports & 2) != 0) offset.x = min(offset.x, 0.0);
            if ((supports & 4) != 0) offset.y = max(offset.y, 0.0);
            if ((supports & 8) != 0) offset.y = min(offset.y, 0.0);
        }
        pos.xz += offset;
        gl_Position = ProjMat * ModelViewMat * vec4(pos, 1.0);
    }
    if (snowSurface) gl_Position.z += gl_Position.w * 0.000001;
    const float chunkFullyVisibleRange = 16.0;
    float dist = length(pos);
    chunkVisibility = mix(1.0, ChunkVisibility, clamp((dist - chunkFullyVisibleRange) / chunkFullyVisibleRange, 0.0, 1.0));
}




