#version 330
#extension GL_ARB_separate_shader_objects : require
#include <minecraft:projection.glsl>
#include <minecraft:dynamictransforms.glsl>
#include <minecraft:fog.glsl>
#include <naturality:water.glsl>
uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
uniform sampler2D NaturalityWaterMask;

layout(location = 0) in vec2 texCoord;
layout(location = 1) flat in mat4 waterInverseProjection;
layout(location = 0) out vec4 fragColor;

vec3 water_view_offset(vec2 uv, float depth) {
    #ifndef RENDERPEARL_DEPTH_IS_ZERO_TO_ONE
    depth = depth * 2.0 - 1.0;
    #endif
    vec4 p = waterInverseProjection * vec4(uv * 2.0 - 1.0, depth, 1.0);
    return p.xyz / p.w;
}

vec3 water_world_offset(vec2 uv, float depth) {
    return transpose(mat3(ModelViewMat)) * water_view_offset(uv, depth);
}

vec2 water_project(vec3 offset) {
    vec4 p = ProjMat * vec4(mat3(ModelViewMat) * offset, 1.0);
    return p.xy / p.w * 0.5 + 0.5;
}

// This is the pre-transparency backdrop, not the final visible water color.
// The same water surface supplies biome color, lighting and atmospheric fog
// over both missing terrain and fully faded terrain.
vec3 water_missing_bed_fill() { return vec3(0.0); }

vec3 above_water_terrain_fade(vec3 color, vec3 offset) {
    float end = min(FogRenderDistanceEnd, max(12.0, WaterParams.w * 0.80));
    float start = min(FogRenderDistanceStart, end * 0.65);
    float fade = smoothstep(start, end, length(offset.xz));
    return mix(color, water_missing_bed_fill(), fade);
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
        vec4 column = naturality_water_geometry(mid.xz);
        // Covered/stacked columns are absent from the exposed-surface map.
        // Keep immersion fog continuous using the actual camera water column.
        bool fallback = WaterTime.y > 0.5 && WaterEnvironment.y == 0 && (column.w == 0.0
                || (WaterCamera.y < column.y - 0.1 && mid.y < column.y));
        if (fallback)
            column = vec4(WaterCamera.y + WaterTime.z, WaterCamera.y - 255.0, 0.0, 1.0);
        if (column.w == 0.0) continue;
        if (abs(ray.y) > 0.0001) {
            float t0 = (column.y - WaterCamera.y) / ray.y;
            float t1 = (column.x - WaterCamera.y) / ray.y;
            a = max(a, min(t0, t1));
            b = min(b, max(t0, t1));
        } else if (mid.y < column.y || mid.y > column.x) continue;
        float segment = max(b - a, 0.0);
        if (segment <= 0.0) continue;
        // Four biome taps are useful only after this interval actually intersects water.
        if (!fallback) column.z = naturality_water_murk(mid.xz);
        float density = mix(0.008, 0.16, column.z);
        wet += segment;
        optical += density * segment;
        scatter += segment;
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
    // Replace missing terrain only behind a rendered water face, before any
    // translucent draws. Water keeps its normal alpha so ice/entities between
    // the surface and the missing bed remain visible in classic and OIT modes.
    float surfaceCoverage = 1.0;
    if (WaterTime.y < 0.5) {
        vec4 surface = texture(NaturalityWaterMask, texCoord);
        surfaceCoverage = clamp(surface.a, 0.0, 1.0);
        if (surface.r <= 0.0 || surfaceCoverage <= 0.0) { fragColor = original; return; }
        if (depth <= 0.0) {
            vec3 surfaceOffset = water_world_offset(texCoord, surface.r);
            vec3 backdrop = above_water_terrain_fade(vec3(0.0), surfaceOffset);
            fragColor = WaterSwitches.x != 0
                ? vec4(mix(original.rgb, backdrop, surfaceCoverage), original.a) : original;
            return;
        }
    }
    vec3 viewOffset = water_view_offset(texCoord, max(depth, 0.000001));
    vec3 offset = transpose(mat3(ModelViewMat)) * viewOffset;
    float distance = depth > 0.0 ? length(offset) : 192.0;
    vec3 ray = normalize(offset);
    // Only above-water absorption consumes the wet-path integral.
    vec3 path = vec3(0.0);
    if (WaterTime.y < 0.5) path = water_path(ray, distance);
    if (path.x <= 0.001 && WaterTime.y < 0.5) {
        // Distant water can lie beyond the bounded optical map/integration.
        // Its visible mask still requires terrain to join the horizon fade.
        vec3 distant = WaterSwitches.x != 0 ? above_water_terrain_fade(original.rgb, offset) : original.rgb;
        fragColor = vec4(mix(original.rgb, distant, surfaceCoverage), original.a);
        return;
    }
    vec2 uv = texCoord;
    vec4 endpointColumn = vec4(0.0);
    bool submergedEndpoint = false;
    if (WaterSwitches.y != 0 && depth > 0.0) {
        endpointColumn = naturality_water_geometry((WaterCamera.xyz + offset).xz);
        submergedEndpoint = naturality_water_depth(WaterCamera.xyz + offset, endpointColumn) > 0.02;
    }
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
        float otherWet = naturality_water_depth(otherPosition, naturality_water_geometry(otherPosition.xz));
        float separation = abs(length(other) - length(offset));
        float tolerance = max(0.75, distance * 0.03);
        refractionWeight = (1.0 - smoothstep(tolerance, tolerance * 3.0, separation))
            * smoothstep(0.0, 0.4, otherWet) * step(0.000001, otherDepth);
        uv = candidate;
    }
    vec3 color = mix(original.rgb, texture(Sampler0, uv).rgb, refractionWeight);
    // Immersed scenery receives shared biome haze below, before translucency.
    // Keep wet-path absorption for views through water from above.
    if (WaterSwitches.x != 0 && WaterTime.y < 0.5) {
        // Apply the submerged palette through the water mask, rather than
        // tinting the dry camera's shared lightmap (which would color land).
        vec3 tint = mix(vec3(1.0), WaterTint.rgb / max(max(WaterTint.r, WaterTint.g), max(WaterTint.b, 0.00392)), 0.5);
        color *= tint * 0.85;
        float transmission = exp(-path.y);
        // Above water the ray can cross a different biome than the camera.
        // Preserve the submerged scene's local color instead of painting it
        // with the camera biome's tint (which creates moving colored patches).
        vec3 scatter = original.rgb * 0.18 * path.z * WaterCamera.w;
        if (WaterSwitches.w != 0) scatter = WaterTint.rgb * 0.22 * max(path.z, 0.2);
        color = mix(scatter, color, transmission);
        // Submerged terrain bypasses terrain atmosphere. Restore its distance
        // fade after water optics, into the exact same fill as absent seabed.
        color = above_water_terrain_fade(color, offset);
    }
    if (WaterSwitches.x != 0 && WaterTime.y > 0.5) {
        // Preserve the old RGBA8 intermediate's rounding before applying fog.
        color = round(clamp(color, 0.0, 1.0) * 255.0) / 255.0;
        vec4 water = texture(NaturalityWaterMask, texCoord);
        float skyOpening = depth <= 0.0 && water.r > 0.0 && water.a > 0.0
            ? smoothstep(0.05, 0.35, ray.y) : 0.0;
        float fogDistance = depth > 0.0 ? length(water_view_offset(texCoord, depth)) : 10000.0;
        if (water.r > 0.0 && water.a > 0.0) fogDistance = min(fogDistance, water.b);
        color = mix(naturality_water_terrain_fog(color, fogDistance), color, skyOpening);
    }
    fragColor = vec4(mix(original.rgb, color, surfaceCoverage), original.a);
}
