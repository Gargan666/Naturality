#ifndef MINECRAFT_FOG_GLSL
#define MINECRAFT_FOG_GLSL

#ifdef NATURALITY_FRAGMENT_FOG
#include <naturality:pixel_fog.glsl>
#endif

layout(std140) uniform Fog {
    vec4 FogColor;
    float FogEnvironmentalStart;
    float FogEnvironmentalEnd;
    float FogRenderDistanceStart;
    float FogRenderDistanceEnd;
    float FogSkyEnd;
    float FogCloudsEnd;
};

float linear_fog_value(float vertexDistance, float fogStart, float fogEnd) {
    #ifdef NATURALITY_FRAGMENT_FOG
    // Evaluate derivatives before branching, but preserve the true distance for
    // nearby haze. Only the dense fog boundary receives the pixel treatment.
    float pixelDistance = naturality_fog_cell_distance(vertexDistance);
    #endif
    if (vertexDistance <= fogStart) {
        return 0.0;
    } else if (vertexDistance >= fogEnd) {
        return 1.0;
    }

    float amount = (vertexDistance - fogStart) / (fogEnd - fogStart);
    #ifdef NATURALITY_FRAGMENT_FOG
    if (!NATURALITY_FOG_ENABLED) return amount;
    float pixelAmount = clamp((pixelDistance - fogStart) / (fogEnd - fogStart), 0.0, 1.0);
    float borderStrength = smoothstep(NATURALITY_FOG_BORDER_START, NATURALITY_FOG_BORDER_FULL, amount);
    return mix(amount, naturality_fog_density(pixelAmount), borderStrength);
    #else
    return amount;
    #endif
}

float total_fog_value(float sphericalVertexDistance, float cylindricalVertexDistance, float environmentalStart, float environmantalEnd, float renderDistanceStart, float renderDistanceEnd) {
    // End edge fog is composited against the captured sky after world rendering.
    if (FogColor.a < 0.0) return 0.0;
    return max(linear_fog_value(sphericalVertexDistance, environmentalStart, environmantalEnd), linear_fog_value(cylindricalVertexDistance, renderDistanceStart, renderDistanceEnd));
}

float naturality_world_fog_brightness(float encodedAlpha) {
    return abs(encodedAlpha) >= 2.0 ? clamp(abs(encodedAlpha) - 2.0, 0.0, 1.0) : 1.0;
}

vec4 apply_fog(vec4 inColor, float sphericalVertexDistance, float cylindricalVertexDistance, float environmentalStart, float environmantalEnd, float renderDistanceStart, float renderDistanceEnd, vec4 fogColor) {
    if (FogColor.a < 0.0) return inColor;
    float fogValue = total_fog_value(sphericalVertexDistance, cylindricalVertexDistance, environmentalStart, environmantalEnd, renderDistanceStart, renderDistanceEnd);
    float opacity = abs(fogColor.a) >= 2.0 ? 1.0 : fogColor.a;
    float caveBrightness = naturality_world_fog_brightness(fogColor.a);
    float distanceFog = linear_fog_value(cylindricalVertexDistance, renderDistanceStart, renderDistanceEnd);
    // Preserve vanilla's max(environmental, distance) coverage, but only tint
    // its environmental portion. At the far boundary the color equals the sky.
    vec3 worldFogColor = fogColor.rgb * caveBrightness;
    vec3 result = mix(inColor.rgb, worldFogColor, fogValue * opacity);
    result += fogColor.rgb * (1.0 - caveBrightness) * distanceFog * opacity;
    return vec4(result, inColor.a);
}

float fog_spherical_distance(vec3 pos) {
    return length(pos);
}

float fog_cylindrical_distance(vec3 pos) {
    float distXZ = length(pos.xz);
    float distY = abs(pos.y);
    return max(distXZ, distY);
}

#endif

