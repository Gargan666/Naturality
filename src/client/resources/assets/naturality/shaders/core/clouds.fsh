#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:fog.glsl>
#include <minecraft:oit.glsl>
#include <minecraft:dynamictransforms.glsl>

layout(location = 0) in float vertexDistance;
layout(location = 1) in vec4 vertexColor;
layout(location = 2) in vec2 fadePosition;
layout(location = 3) flat in float fadeRows;
layout(location = 4) flat in vec2 capWeights;
layout(location = 5) in vec3 cloudLocal;
layout(location = 6) flat in vec3 cloudBase;
layout(location = 7) flat in int edgeMask;
layout(location = 8) flat in int cloudDirection;

layout(std140) uniform CloudInfo {
    vec4 CloudColor;
    vec3 CloudOffset;
    vec3 CellSize;
    vec4 FadeSettings;
    vec4 MoonLight;
    vec4 CloudTextureSize;
};

vec3 cloud_moon_tint() {
    float pixelSize = CellSize.y / max(CloudTextureSize.z, 3.0);
    vec3 pixel = floor(cloudLocal / pixelSize);
    vec3 local = clamp((pixel + 0.5) * pixelSize, vec3(0), CellSize);
    // Keep the normal coordinate on the face, snapping only its two surface axes.
    if (cloudDirection < 2) local.y = cloudDirection == 0 ? 0.0 : CellSize.y;
    else if (cloudDirection < 4) local.z = cloudDirection == 2 ? 0.0 : CellSize.z;
    else local.x = cloudDirection == 4 ? 0.0 : CellSize.x;
    vec3 relative = cloudBase + local;
    float alignment = dot(mat3(ModelViewMat) * (relative / max(length(relative), 0.0001)), MoonLight.xyz);
    float halo = smoothstep(0.60, 0.99, alignment);
    float rim = 1.0;
    if (cloudDirection < 2) {
        float inward = 1e6;
        if ((edgeMask & 8) != 0) inward = min(inward, local.z);
        if ((edgeMask & 4) != 0) inward = min(inward, CellSize.x - local.x);
        if ((edgeMask & 2) != 0) inward = min(inward, CellSize.z - local.z);
        if ((edgeMask & 1) != 0) inward = min(inward, local.x);
        // Empty diagonal texels expose a concave corner even when both side neighbors
        // are occupied. Carry the glow across that corner instead of ending at the seam.
        if ((edgeMask & 16) != 0) inward = min(inward, length(local.xz));
        if ((edgeMask & 32) != 0) inward = min(inward, length(vec2(CellSize.x - local.x, local.z)));
        if ((edgeMask & 64) != 0) inward = min(inward, length(CellSize.xz - local.xz));
        if ((edgeMask & 128) != 0) inward = min(inward, length(vec2(local.x, CellSize.z - local.z)));
        float reach = min(CellSize.x * 0.4, CellSize.y * 0.75);
        rim = 1.0 - smoothstep(0.0, max(reach, pixelSize), inward);
    }
    vec2 anchored = mod((cloudBase.xz - CloudOffset.xz) / CellSize.x + FadeSettings.zw, CloudTextureSize.xy);
    float variation = fract(sin(dot(anchored * (CellSize.x / pixelSize) + pixel.xz, vec2(127.1, 311.7))) * 43758.5453);
    vec3 tint = mix(vec3(0.32, 0.27, 0.19), vec3(0.55, 0.62, 0.72), smoothstep(0.8, 1.0, alignment));
    return tint * halo * rim * mix(0.85, 1.0, variation) * MoonLight.w;
}

#ifndef OIT_ALPHA_ONLY
layout(location = 0) out vec4 fragColor;
#endif

vec4 calculateFinalColor(vec4 color) {
    #ifdef OIT_ACCUMULATE
    color = sampleColorForAccumulation(color);
    #endif
    return color;
}

void main() {
    vec4 color = vertexColor;
    color.rgb = clamp(color.rgb + cloud_moon_tint(), 0.0, 1.0);
    if (fadeRows >= 2.0) {
        // Only gradient side walls are one-sided. Horizontal planes have fadeRows = 0
        // and remain visible from both directions, including the cloud bottom.
        if (!gl_FrontFacing) discard;
        // Square, surface-aligned pixels; each row has a discrete opacity, including zero at the top.
        float rows = max(fadeRows, 3.0);
        vec2 pixel = floor(fadePosition * rows);
        float row = clamp(pixel.y, 0.0, rows - 1.0);
        float checker = mod(pixel.x + pixel.y, 2.0) * 0.3;
        float upward = row == 0.0 ? 1.0 : (row >= rows - 1.0 ? 0.0 : 1.0 - (row + checker) / (rows - 1.0));
        float downward = row >= rows - 1.0 ? 1.0 : (row == 0.0 ? 0.0 : (row - checker) / (rows - 1.0));
        // Normalize the symmetric tent so even pixel counts still reach full color.
        float center = (rows - 1.0) * 0.5;
        float peak = floor(center);
        float middle = clamp((center - abs(row - center)) / max(peak, 1.0), 0.0, 1.0);
        float fade = capWeights.x * upward + capWeights.y * downward
            + (1.0 - capWeights.x - capWeights.y) * middle;
        color.a *= fade;
    }
    if (color.a <= 0.0) discard;
    #ifndef OIT_DEPTH_BOUNDS
    color.a *= 1.0f - linear_fog_value(vertexDistance, FogCloudsEnd * 0.75, FogCloudsEnd);
    if (color.a <= 0.0) discard;
    #endif

    #ifdef OIT_ALPHA_ONLY
    executeAlphaOnlyPhase(gl_FragCoord.z, color.a);
    #else
    fragColor = calculateFinalColor(color);
    #endif
}
