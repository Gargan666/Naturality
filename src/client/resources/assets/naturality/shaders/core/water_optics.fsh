#version 330
#extension GL_ARB_separate_shader_objects : require
#include <minecraft:projection.glsl>
#include <minecraft:dynamictransforms.glsl>
#include <naturality:water.glsl>
uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
uniform sampler2D NaturalityWaterMask;
layout(location = 0) in vec2 texCoord;
layout(location = 0) out vec4 fragColor;

vec3 water_world_offset(vec2 uv, float depth) {
    #ifndef RENDERPEARL_DEPTH_IS_ZERO_TO_ONE
    depth = depth * 2.0 - 1.0;
    #endif
    vec4 p = inverse(ProjMat) * vec4(uv * 2.0 - 1.0, depth, 1.0);
    return transpose(mat3(ModelViewMat)) * (p.xyz / p.w);
}

vec2 water_project(vec3 offset) {
    vec4 p = ProjMat * vec4(mat3(ModelViewMat) * offset, 1.0);
    return p.xy / p.w * 0.5 + 0.5;
}

vec2 water_cell_wave(ivec2 cell) {
    uvec2 h = uvec2(cell) * uvec2(1597334677u, 3812015801u);
    h ^= h.yx >> 16u;
    vec2 phase = vec2(h & uvec2(65535u)) / 65535.0 * 6.2831853;
    // Coarse cells, continuous animation: no integer-offset time snapping.
    return sin(phase + WaterTime.x * vec2(0.8, -0.65));
}

vec2 water_continuous_wave(vec2 localGrid, ivec2 origin) {
    ivec2 cell = ivec2(floor(localGrid)) + origin;
    vec2 f = fract(localGrid);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(water_cell_wave(cell), water_cell_wave(cell + ivec2(1, 0)), f.x),
        mix(water_cell_wave(cell + ivec2(0, 1)), water_cell_wave(cell + ivec2(1, 1)), f.x), f.y);
}

// Integrate only wet intervals. Each interval is clipped analytically in Y so
// even a one-block pond is retained when viewed from high above it.
vec3 water_path(vec3 ray, float distance) {
    float wet = 0.0, optical = 0.0, scatter = 0.0;
    float stepLength = min(distance, 192.0) / 24.0;
    for (int i = 0; i < 24; i++) {
        float a = float(i) * stepLength, b = a + stepLength;
        vec3 mid = WaterCamera.xyz + ray * ((a + b) * 0.5);
        vec4 column = naturality_water_column(mid.xz);
        // Covered/stacked columns are absent from the exposed-surface map.
        // Keep immersion fog continuous using the actual camera water column.
        if (WaterTime.y > 0.5 && WaterEnvironment.y == 0 && (column.w == 0.0
                || (WaterCamera.y < column.y - 0.1 && mid.y < column.y)))
            column = vec4(WaterCamera.y + WaterTime.z, WaterCamera.y - 255.0, 0.0, 1.0);
        if (column.w == 0.0) continue;
        if (abs(ray.y) > 0.0001) {
            float t0 = (column.y - WaterCamera.y) / ray.y;
            float t1 = (column.x - WaterCamera.y) / ray.y;
            a = max(a, min(t0, t1));
            b = min(b, max(t0, t1));
        } else if (mid.y < column.y || mid.y > column.x) continue;
        float segment = max(b - a, 0.0);
        float depth = max(column.x - (WaterCamera.y + ray.y * (a + b) * 0.5), 0.0);
        float dark = 1.0 - naturality_water_daylight(depth, column.z);
        float density = mix(0.012 + 0.16 * dark, 0.65, column.z);
        wet += segment;
        optical += density * segment;
        scatter += segment * exp(-depth / mix(12.0, 2.0, column.z));
    }
    return vec3(wet, optical, scatter / max(wet, 0.001));
}

void main() {
    vec4 original = texture(Sampler0, texCoord);
    if (WaterMap.w == 0) { fragColor = original; return; }
    float depth = texture(Sampler1, texCoord).r;
    // The column map contains loaded water well beyond the terrain draw range.
    // It is an optical-property lookup, not proof that this pixel sees water.
    // From air, only shade opaque scenery behind an actually visible water face.
    // Empty sky is handled by the water material's missing-bed fill later;
    // tracing it here projects invisible water volumes onto the atmosphere.
    if (WaterTime.y < 0.5) {
        vec4 surface = texture(NaturalityWaterMask, texCoord);
        if (depth <= 0.0 || surface.a < 0.5) { fragColor = original; return; }
    }
    vec3 offset = water_world_offset(texCoord, max(depth, 0.000001));
    float distance = depth > 0.0 ? length(offset) : 192.0;
    vec3 ray = normalize(offset);
    vec3 path = water_path(ray, distance);
    if (path.x <= 0.001 && WaterTime.y < 0.5) { fragColor = original; return; }
    vec2 uv = texCoord;
    vec4 endpointColumn = naturality_water_column((WaterCamera.xyz + offset).xz);
    bool submergedEndpoint = depth > 0.0 && naturality_water_depth(WaterCamera.xyz + offset, endpointColumn) > 0.02;
    float refractionWeight = 0.0;
    if (WaterSwitches.y != 0 && submergedEndpoint) {
        vec2 size = vec2(textureSize(Sampler0, 0));
        vec2 delta;
        if (WaterTime.y < 0.5) {
            float cellSize = exp2(floor(log2(max(WaterParams.z, 1.0)))) / 8.0;
            float t = clamp((endpointColumn.x - WaterCamera.y) / min(ray.y, -0.0001), 0.0, distance);
            vec2 surface = WaterCamera.xz + ray.xz * t;
            // Shared lattice interpolation crosses every block/cell boundary
            // continuously; adjacent tiles no longer pull scenery apart.
            vec2 wave = water_continuous_wave(surface / cellSize, ivec2(vec2(WaterMap.xy) / cellSize));
            float submergedDepth = max(endpointColumn.x - (WaterCamera.y + offset.y), 0.0);
            float amplitude = 0.32 * (1.0 - exp(-submergedDepth / 14.0));
            delta = water_project(offset + vec3(wave.x, 0.0, wave.y) * amplitude) - texCoord;
        } else {
            float pixels = max(8.0, WaterParams.z * 3.0);
            vec2 wave = water_continuous_wave(gl_FragCoord.xy / pixels, ivec2(0));
            delta = wave * 0.65 * smoothstep(1.0, 4.0, distance) / size;
        }
        vec2 candidate = clamp(uv + delta, 0.5 / size, 1.0 - 0.5 / size);
        float otherDepth = texture(Sampler1, candidate).r;
        vec3 other = water_world_offset(candidate, max(otherDepth, 0.000001));
        vec3 otherPosition = WaterCamera.xyz + other;
        // Reject air/sky and large depth jumps: dry foreground silhouettes do not bleed into water.
        float otherWet = naturality_water_depth(otherPosition, naturality_water_column(otherPosition.xz));
        float separation = abs(length(other) - length(offset));
        float tolerance = max(0.75, distance * 0.03);
        refractionWeight = (1.0 - smoothstep(tolerance, tolerance * 3.0, separation))
            * smoothstep(0.0, 0.4, otherWet) * step(0.000001, otherDepth);
        uv = candidate;
    }
    vec3 color = mix(original.rgb, texture(Sampler0, uv).rgb, refractionWeight);
    if (WaterSwitches.x != 0) {
        // A broad upward window transmits the surface. Horizontal/downward rays
        // keep their full extinction; no universal black overlay hides local lights.
        float upwardWindow = WaterTime.y * smoothstep(0.15, 0.85, ray.y);
        float transmission = exp(-path.y * mix(1.0, 0.07, upwardWindow));
        float illumination = WaterTime.y > 0.5 ? WaterTime.w : WaterCamera.w;
        // Above water the ray can cross a different biome than the camera.
        // Preserve the submerged scene's local color instead of painting it
        // with the camera biome's tint (which creates moving colored patches).
        vec3 scatterTint = WaterTime.y > 0.5 ? WaterTint.rgb : original.rgb;
        vec3 scatter = scatterTint * 0.18 * path.z * illumination;
        if (WaterSwitches.w != 0) scatter = WaterTint.rgb * 0.22 * max(path.z, 0.2);
        color = mix(scatter, color, transmission);
    }
    fragColor = vec4(color, original.a);
}
