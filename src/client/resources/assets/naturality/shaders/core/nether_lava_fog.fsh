#version 330
#extension GL_ARB_separate_shader_objects : require
#include <minecraft:dynamictransforms.glsl>
#include <minecraft:fog.glsl>
#include <naturality:lava_fog.glsl>
uniform sampler2D Sampler0;
uniform sampler2D LavaColumns;
#ifndef NATURALITY_LAVA_INTEGRATION_SEGMENTS
#define NATURALITY_LAVA_INTEGRATION_SEGMENTS 32
#endif
layout(std140) uniform LavaFog {
    vec4 LavaGrid;
    vec4 LavaBounds;
    vec4 LavaCache;
};
layout(location = 0) in vec2 texCoord;
layout(location = 1) flat in mat4 inverseProjection;
layout(location = 0) out vec4 fragColor;

vec3 world_offset(float depth) {
#ifndef RENDERPEARL_DEPTH_IS_ZERO_TO_ONE
    depth = depth * 2.0 - 1.0;
#endif
    vec4 view = inverseProjection * vec4(texCoord * 2.0 - 1.0, depth, 1.0);
    return transpose(mat3(ModelViewMat)) * (view.xyz / view.w);
}

float column_density(ivec2 cell, float height) {
    int size = int(LavaGrid.w);
    bool valid = all(greaterThanEqual(cell, ivec2(0))) && all(lessThan(cell, ivec2(size)));
    ivec2 address = clamp(cell, ivec2(0), ivec2(size - 1)) + ivec2(LavaCache.xy);
    address -= ivec2(greaterThanEqual(address, ivec2(size))) * size;
    vec4 column = texelFetch(LavaColumns, address, 0);
    // The layer follows the actual lake and roof, never the camera's altitude.
    float aboveSurface = height + LavaCache.z - column.x;
    float roof = min(column.y - column.x, 40.0);
    float vertical = smoothstep(-1.0, 2.0, aboveSurface)
        * (1.0 - smoothstep(min(4.0, roof * 0.25), max(0.001, roof), aboveSurface));
    // Retiring samples retain their heights and starting brightness until the
    // reverse ease reaches zero. Refreshes do not restart either transition.
    bool retiring = column.w < 0.0;
    float age = retiring ? LavaCache.w + column.w + 1.0 : LavaCache.w - column.w;
    float eased = smoothstep(0.0, LavaBounds.z, age);
    float visibility = retiring ? 1.0 - eased : eased;
    return float(valid) * column.z * vertical * visibility;
}

float lava_density(vec3 point) {
    vec2 grid = (point.xz - LavaGrid.xy) / LavaGrid.z;
    // Each contributing column fades to zero at its own lake/roof. A nearest-cell
    // validity or roof cutoff here would introduce eight-block vertical curtains.
    ivec2 cell = ivec2(floor(grid));
    vec2 blend = smoothstep(vec2(0.0), vec2(1.0), fract(grid));
    // Interpolate density, never heights: disconnected lakes remain disconnected.
    vec2 edge = min(grid + 0.5, vec2(LavaGrid.w - 0.5) - grid) * LavaGrid.z;
    return smoothstep(0.0, 16.0, min(edge.x, edge.y))
        * mix(mix(column_density(cell, point.y), column_density(cell + ivec2(1, 0), point.y), blend.x),
        mix(column_density(cell + ivec2(0, 1), point.y), column_density(cell + ivec2(1, 1), point.y), blend.x), blend.y);
}

float fog_amount(float distance, float start, float end) {
    return clamp((distance - start) / max(end - start, 0.001), 0.0, 1.0);
}

float glow_density(vec3 ray, float distance) {
    vec3 point = ray * distance;
    float fog = max(fog_amount(distance, FogEnvironmentalStart, FogEnvironmentalEnd),
        fog_amount(fog_cylindrical_distance(point), FogRenderDistanceStart, FogRenderDistanceEnd));
    return lava_density(point) * fog * fog;
}

float integrated_glow(vec3 ray, float start, float end) {
    if (end <= start) return 0.0;
    float glow = 0.0;
    float stepLength = (end - start) / float(NATURALITY_LAVA_INTEGRATION_SEGMENTS);
    // Integrate each continuous interval directly, including the surface endpoint.
    // A binary depth test at individual samples would introduce radial bands.
    for (int sampleIndex = 0; sampleIndex < NATURALITY_LAVA_INTEGRATION_SEGMENTS; sampleIndex++) {
        float middle = start + (float(sampleIndex) + 0.5) * stepLength;
        float offset = stepLength * 0.288675134595;
        glow += (glow_density(ray, middle - offset) + glow_density(ray, middle + offset)) * stepLength / 24.0;
    }
    return glow;
}

vec4 glow_color(float glow) {
    float strength = 1.0 - exp(-glow * 1.8);
    vec3 color = naturality_lava_fog_color(strength);
    float alpha = clamp(strength, 0.0, 0.98);
    return vec4(color * alpha, alpha);
}

void main() {
    float depth = texture(Sampler0, texCoord).r;
    vec3 position = world_offset(max(depth, 0.000001));
    float sceneDistance = length(position);
    vec3 ray = position / max(sceneDistance, 0.0001);
    float start = 0.0;
    float end = 288.0;
    // Only integrate inside the surveyed field. This bounds the atmosphere itself,
    // without creating any extra terrain silhouettes or occlusion planes.
    vec2 low = LavaGrid.xy - vec2(LavaGrid.z * 0.5);
    vec2 high = low + vec2(LavaGrid.w * LavaGrid.z);
    vec2 exitDistance = mix(low, high, greaterThanEqual(ray.xz, vec2(0.0)))
        / (sign(ray.xz + vec2(1e-10)) * max(abs(ray.xz), vec2(1e-8)));
    end = min(end, min(exitDistance.x, exitDistance.y));
    if (abs(ray.y) > 0.00001) {
        float a = LavaBounds.x / ray.y, b = LavaBounds.y / ray.y;
        if (max(a, b) <= 0.0 || min(a, b) >= end) { fragColor = vec4(0.0); return; }
        start = max(0.0, min(a, b));
        end = min(end, max(a, b));
    } else if (LavaBounds.x > 0.0 || LavaBounds.y < 0.0) {
        fragColor = vec4(0.0); return;
    }

    if (end <= start) { fragColor = vec4(0.0); return; }
    float surfaceFog = max(fog_amount(sceneDistance, FogEnvironmentalStart, FogEnvironmentalEnd),
        fog_amount(fog_cylindrical_distance(position), FogRenderDistanceStart, FogRenderDistanceEnd));
    // Color fog preserves wall occlusion through most of the span. Release the
    // atmosphere behind terrain only at the far boundary, where loaded terrain
    // must converge with an empty depth buffer to conceal chunk edges.
    // Glow in front of the wall still integrates up to its exact scene depth.
    float atmosphereVisibility = naturality_lava_atmosphere_visibility(surfaceFog);
    vec4 result;
    if (atmosphereVisibility >= 1.0 || sceneDistance >= end) {
        result = glow_color(integrated_glow(ray, start, end));
    } else {
        result = glow_color(integrated_glow(ray, start, min(end, sceneDistance)));
        if (atmosphereVisibility > 0.0) {
            vec4 atmosphere = glow_color(integrated_glow(ray, start, end));
            result = mix(result, atmosphere, atmosphereVisibility);
        }
    }
    // The render pipeline uses straight-alpha blending; the crossfade above must
    // use premultiplied colors to avoid a dark fringe while either side is clear.
    fragColor = vec4(result.rgb / max(result.a, 0.000001), result.a);
}
