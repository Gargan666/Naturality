#version 330
#extension GL_ARB_separate_shader_objects : require
#include <minecraft:projection.glsl>
#include <minecraft:dynamictransforms.glsl>
#include <minecraft:fog.glsl>
#include <naturality:water.glsl>
uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
uniform sampler2D NaturalityWaterMask;
uniform sampler2D NaturalityWaterMaskDepth;
uniform sampler2D NaturalityWaterAtmosphere;

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
vec3 water_missing_bed_fill(vec3 offset) {
    // A roofed pocket still looks into the underwater lower hemisphere, even
    // where terrain is absent or reaches the render-distance boundary.
    return WaterEnvironment.w != 0 ? naturality_water_directional_fog_color(normalize(offset).y)
        : naturality_water_fog_color();
}

vec3 above_water_terrain_fade(vec3 color, vec3 offset) {
    float end = min(FogRenderDistanceEnd, max(12.0, WaterParams.w * 0.80));
    float start = min(FogRenderDistanceStart, end * 0.65);
    float distance = max(length(offset.xz), abs(offset.y));
    float fade = smoothstep(start, end, distance);
    vec3 water = mix(color, water_missing_bed_fill(offset), fade);
    float atmosphereFade = naturality_water_fog_coverage(smoothstep(
        FogRenderDistanceEnd * 0.80, FogRenderDistanceEnd, distance));
    return mix(water, texture(NaturalityWaterAtmosphere, texCoord).rgb, atmosphereFade);
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
float water_path(vec3 ray, float distance, float entry) {
    float wet = 0.0;
    float stepLength = min(max(distance - entry, 0.0), 192.0) / 24.0;
    for (int i = 0; i < 24; i++) {
        float a = entry + float(i) * stepLength, b = a + stepLength;
        vec3 mid = WaterCamera.xyz + ray * ((a + b) * 0.5);
        vec4 column = naturality_water_geometry(mid.xz);
        // The visible entry face proves water after the dry pocket. Roofs and
        // stacked pools can be absent/below the exposed-column map; do not
        // interpret that missing data as air. Ordinary air views and falling
        // sheets keep their mapped intervals. Never count the dry approach.
        bool covered = WaterEnvironment.w != 0 && (column.w == 0.0 || mid.y < column.y);
        if (covered) { wet += b - a; continue; }
        if (column.w == 0.0) continue;
        if (abs(ray.y) > 0.0001) {
            float t0 = (column.y - WaterCamera.y) / ray.y;
            float t1 = (column.x - WaterCamera.y) / ray.y;
            a = max(a, min(t0, t1));
            b = min(b, max(t0, t1));
        } else if (mid.y < column.y || mid.y > column.x) continue;
        float segment = max(b - a, 0.0);
        if (segment <= 0.0) continue;
        wet += segment;
    }
    return wet;
}

void main() {
    vec4 original = texture(Sampler0, texCoord);
    if (WaterMap.w == 0) { fragColor = original; return; }
    float depth = texture(Sampler1, texCoord).r;
    bool splitView = WaterLens.z > 0.5;
    // Reversed-Z depth 1 is the actual near plane, including pitch and roll.
    // Water faces clipped before this plane cannot supply a surface-mask pixel.
    bool immersed = splitView ? naturality_water_lens_depth(water_world_offset(texCoord, 1.0)) >= 0.0
        : WaterTime.y > 0.5;
    vec4 surface = texture(NaturalityWaterMask, texCoord);
    if (splitView && immersed) {
        // The private depth mask also contains vanilla's posed dry boat hull.
        // Do not confuse that invisible patch with water clipped by the lens.
        float maskDepth = texture(NaturalityWaterMaskDepth, texCoord).r;
        if (maskDepth > depth + 0.000001 && (surface.r <= 0.0 || maskDepth > surface.r + 0.000001))
            immersed = false;
    }
    // The column map contains loaded water well beyond the terrain draw range.
    // It is an optical-property lookup, not proof that this pixel sees water.
    // From air, only shade opaque scenery behind an actually visible water face.
    // Replace missing terrain only behind a rendered water face, before any
    // translucent draws. Water keeps its normal alpha so ice/entities between
    // the surface and the missing bed remain visible in classic and OIT modes.
    float surfaceCoverage = 1.0;
    if (!immersed) {
        surfaceCoverage = clamp(surface.a, 0.0, 1.0);
        if (surface.r <= 0.0 || surfaceCoverage <= 0.0) { fragColor = original; return; }
        if (depth <= 0.0) {
            vec3 surfaceOffset = water_world_offset(texCoord, surface.r);
            vec3 backdrop = above_water_terrain_fade(water_missing_bed_fill(surfaceOffset), surfaceOffset);
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
    float path = 0.0;
    if (!immersed) path = water_path(ray, distance, length(water_world_offset(texCoord, surface.r)));
    if (path <= 0.001 && !immersed) {
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
        if (!immersed) {
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
        if (splitView && (naturality_water_lens_depth(water_world_offset(candidate, 1.0)) >= 0.0) != immersed)
            refractionWeight = 0.0;
        uv = candidate;
    }
    vec3 color = mix(original.rgb, texture(Sampler0, uv).rgb, refractionWeight);
    // Immersed scenery receives shared biome haze below, before translucency.
    // Keep wet-path absorption for views through water from above.
    if (WaterSwitches.x != 0 && (!immersed || splitView)) {
        // Apply the submerged palette through the water mask, rather than
        // tinting the dry camera's shared lightmap (which would color land).
        vec3 tint = mix(vec3(1.0), WaterTint.rgb / max(max(WaterTint.r, WaterTint.g), max(WaterTint.b, 0.00392)), 0.5);
        color *= tint * 0.85;
    }
    if (WaterSwitches.x != 0 && !immersed) {
        // Crossing into air must not remove the underwater silhouette/haze.
        // Count only the wet part of the sightline, excluding camera altitude.
        color = naturality_water_path_fog(color, path, 1.0, ray.y);
        // Submerged terrain bypasses terrain atmosphere. Restore its distance
        // fade after water optics, into the exact same fill as absent seabed.
        color = above_water_terrain_fade(color, offset);
    }
    if (WaterSwitches.x != 0 && immersed) {
        // Preserve the old RGBA8 intermediate's rounding before applying fog.
        color = round(clamp(color, 0.0, 1.0) * 255.0) / 255.0;
        vec4 water = texture(NaturalityWaterMask, texCoord);
        float skyOpening = depth <= 0.0 && water.r > 0.0 && water.a > 0.0
            ? smoothstep(0.05, 0.35, ray.y) * naturality_water_sun_transmission() : 0.0;
        float fogDistance = depth > 0.0 ? length(water_view_offset(texCoord, depth)) : 10000.0;
        // A nearby water face at entry is not the distance to the seabed.
        // Only bound sky haze by the overhead exit; keep actual terrain depth.
        if (depth <= 0.0 && ray.y > 0.0 && water.r > 0.0 && water.a > 0.0)
            fogDistance = min(fogDistance, water.b);
        color = mix(naturality_water_path_fog(color, fogDistance, 1.0, ray.y), color, skyOpening);
    }
    fragColor = vec4(mix(original.rgb, color, surfaceCoverage), original.a);
}
