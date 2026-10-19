#ifndef NATURALITY_WATER_GLSL
#define NATURALITY_WATER_GLSL
#ifdef NATURALITY_FRAGMENT_FOG
#include <naturality:pixel_fog.glsl>
#endif
layout(std140) uniform NaturalityWater {
    ivec4 WaterMap; // origin X/Z, world minimum Y, enabled
    vec4 WaterCamera; // map-relative X/Z, world Y, sunlight
    vec4 WaterParams; // full fog distance, opaque shading limit (0 disables), refraction pixel size, render distance
    ivec4 WaterSwitches; // depth, distortion, shimmer, night vision
    vec4 WaterTime; // seconds, immersed, camera water depth, local illumination
    vec4 WaterTint; // biome-blended current water color, camera turbidity
    ivec4 WaterEnvironment; // Overworld atmosphere, falling water, float bits of rain fog strength, nearby submerged water
    vec4 WaterLens; // map-relative tile origin X/Z, local split enabled, reserved
    vec4 WaterLensForward; // camera forward, near-plane distance
    vec4 WaterLensHeights[9]; // actual rendered NW, SW, SE, NE surface heights
    vec4 WaterLensBounds[9]; // block bottom, cauldron inset, validity, reserved
    vec4 WaterFogColor; // frame-shared base fog RGB, reserved
};
uniform sampler2D NaturalityWaterColumns;

// Classify the lens rather than the eye. Match the two triangles of the actual
// fluid mesh, including sloping flow and gaps at shore/cauldron boundaries.
float naturality_water_lens_depth(vec3 nearOffset) {
    vec3 p = WaterCamera.xyz + nearOffset;
    vec2 tile = p.xz - WaterLens.xy;
    ivec2 cell = ivec2(floor(tile));
    if (any(lessThan(cell, ivec2(0))) || any(greaterThan(cell, ivec2(2)))) return -1.0;
    int index = cell.y * 3 + cell.x;
    vec4 bounds = WaterLensBounds[index];
    vec2 f = fract(tile);
    if (bounds.z == 0.0 || p.y < bounds.x || any(lessThan(f, vec2(bounds.y)))
            || any(greaterThanEqual(f, vec2(1.0 - bounds.y)))) return -1.0;
    vec4 h = WaterLensHeights[index];
    float top = f.y >= f.x ? h.x + (h.z - h.y) * f.x + (h.y - h.x) * f.y
        : h.x + (h.w - h.x) * f.x + (h.z - h.w) * f.y;
    return top - p.y;
}

float naturality_water_view_immersion(vec3 offset) {
    if (WaterLens.z == 0.0) return WaterTime.y;
    vec3 nearOffset = offset * (WaterLensForward.w / max(dot(offset, WaterLensForward.xyz), 0.00001));
    return step(0.0, naturality_water_lens_depth(nearOffset));
}

float naturality_water_visibility_range() {
    // Calculated once per frame by WaterVisuals.visibilityRange, so CPU
    // visibility decisions and all water shaders use the same endpoint.
    return WaterParams.x;
}

float naturality_water_fresnel(float facing) {
    facing = clamp(abs(facing), 0.0, 1.0);
    float reflection = 0.02 + 0.98 * pow(1.0 - facing, 5.0);
    // Stylized grazing cutoff keeps the pixel surface opaque at shallow angles.
    return mix(1.0, reflection, smoothstep(0.15, 0.50, facing));
}

float naturality_water_fog_coverage(float amount) {
    #ifdef NATURALITY_FRAGMENT_FOG
    if (NATURALITY_FOG_ENABLED) return naturality_fog_density(amount);
    #endif
    return amount;
}

float naturality_water_distance_fade(float distance) {
    float end = naturality_water_visibility_range();
    return naturality_water_fog_coverage(smoothstep(end * 0.18, end, distance)) * WaterTime.y;
}

float naturality_water_surface_fade(float distance, vec3 offset) {
    float end = naturality_water_visibility_range();
    return naturality_water_fog_coverage(smoothstep(end * 0.18, end, distance))
        * naturality_water_view_immersion(offset);
}

float naturality_water_murk(vec2 position) {
    vec2 p = position - 0.5;
    ivec2 cell = ivec2(floor(p));
    vec2 f = fract(p);
    float sum = 0.0, weight = 0.0;
    for (int z = 0; z < 2; z++) for (int x = 0; x < 2; x++) {
        float a = texelFetch(NaturalityWaterColumns, clamp(cell + ivec2(x, z), ivec2(0), ivec2(255)), 0).a * 255.0;
        float w = (x == 0 ? 1.0 - f.x : f.x) * (z == 0 ? 1.0 - f.y : f.y) * step(0.5, a);
        sum += max(a - 1.0, 0.0) / 254.0 * w;
        weight += w;
    }
    return sum / max(weight, 0.0001);
}

// Surface, bottom, turbidity, validity. Integer origin subtraction precedes float conversion.
vec4 naturality_water_geometry(vec2 position) {
    ivec2 cell = ivec2(floor(position));
    if (WaterMap.w == 0 || any(lessThan(cell, ivec2(0))) || any(greaterThanEqual(cell, ivec2(256)))) return vec4(0);
    ivec4 data = ivec4(round(texelFetch(NaturalityWaterColumns, cell, 0) * 255.0));
    float top = float(WaterMap.z + data.r + (data.g & 127) * 256) + 0.875;
    return vec4(top, top - float(data.b) + 0.125, 0.0, data.a > 0 ? 1.0 : 0.0);
}
vec4 naturality_water_column(vec2 position) {
    ivec2 cell = ivec2(floor(position));
    if (WaterMap.w == 0 || any(lessThan(cell, ivec2(0))) || any(greaterThanEqual(cell, ivec2(256)))) return vec4(0);
    vec4 column = naturality_water_geometry(position);
    column.z = naturality_water_murk(position);
    return column;
}
// Cover is geometric, not skylight brightness: transparent ice still blocks shafts.
float naturality_water_open_surface(vec2 position) {
    ivec2 cell = ivec2(floor(position));
    if (WaterMap.w == 0 || any(lessThan(cell, ivec2(0))) || any(greaterThanEqual(cell, ivec2(256)))) return 0.0;
    ivec4 data = ivec4(round(texelFetch(NaturalityWaterColumns, cell, 0) * 255.0));
    return data.a > 0 && (data.g & 128) == 0 ? 1.0 : 0.0;
}
float naturality_water_depth(vec3 p, vec4 column) {
    return column.w > 0.0 && p.y >= column.y - 0.08 ? max(column.x - p.y, 0.0) : 0.0;
}
vec3 naturality_water_fog_color() {
    // Prepared from this frame's uploaded column map and lighting; directional
    // darkening and ordered pixel coverage remain per fragment below.
    return WaterFogColor.rgb;
}

vec3 naturality_water_directional_fog_color(float rayY) {
    // As the viewer descends, the dark lower hemisphere rises toward zenith.
    float depthRise = smoothstep(4.0, 80.0, WaterTime.z) * 1.85;
    return naturality_water_fog_color()
        * (1.0 - smoothstep(0.0, 0.85, max(depthRise - rayY, 0.0)));
}

float naturality_water_sun_transmission() {
    return 1.0 - smoothstep(4.0, 64.0, WaterTime.z);
}

vec3 naturality_water_path_fog(vec3 color, float distance, float strength, float rayY) {
    float range = naturality_water_visibility_range();
    vec3 fog = naturality_water_directional_fog_color(rayY);
    // Like End fog, remove texture/light variation first, then dissolve the
    // solid silhouette. Keep the silhouette in the biome's water palette.
    float silhouette = naturality_water_fog_coverage(smoothstep(range * 0.25, range * 0.60, distance)) * strength;
    float fade = naturality_water_fog_coverage(smoothstep(range * 0.65, range, distance)) * strength;
    return mix(mix(color, fog * 0.22, silhouette), fog, fade);
}
vec3 naturality_water_terrain_fog(vec3 color, float distance, float rayY) {
    return naturality_water_path_fog(color, distance, WaterTime.y, rayY);
}
#endif
