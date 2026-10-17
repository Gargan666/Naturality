#ifndef NATURALITY_WATER_GLSL
#define NATURALITY_WATER_GLSL
layout(std140) uniform NaturalityWater {
    ivec4 WaterMap; // origin X/Z, world minimum Y, enabled
    vec4 WaterCamera; // map-relative X/Z, world Y, sunlight
    vec4 WaterParams; // reserved, reserved, refraction pixel size, render distance
    ivec4 WaterSwitches; // depth, distortion, shimmer, night vision
    vec4 WaterTime; // seconds, immersed, camera water depth, local illumination
    vec4 WaterTint; // biome-blended current water color, camera turbidity
    ivec4 WaterEnvironment; // Overworld atmosphere, falling water, float bits of rain fog strength, reserved
};
uniform sampler2D NaturalityWaterColumns;

float naturality_water_visibility_range() {
    // Follow the effective terrain draw distance without a fixed visibility cap.
    // Both silhouette stages and surface haze use this same range.
    float end = max(12.0, WaterParams.w * 0.65);
    end *= mix(1.0, 0.45, WaterTint.a);
    return end;
}

float naturality_water_distance_fade(float distance) {
    float end = naturality_water_visibility_range();
    return smoothstep(end * 0.18, end, distance) * WaterTime.y;
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
    float top = float(WaterMap.z + data.r + data.g * 256) + 0.875;
    return vec4(top, top - float(data.b) + 0.125, 0.0, data.a > 0 ? 1.0 : 0.0);
}
vec4 naturality_water_column(vec2 position) {
    ivec2 cell = ivec2(floor(position));
    if (WaterMap.w == 0 || any(lessThan(cell, ivec2(0))) || any(greaterThanEqual(cell, ivec2(256)))) return vec4(0);
    vec4 column = naturality_water_geometry(position);
    column.z = naturality_water_murk(position);
    return column;
}
float naturality_water_depth(vec3 p, vec4 column) {
    return column.w > 0.0 && p.y >= column.y - 0.08 ? max(column.x - p.y, 0.0) : 0.0;
}
vec3 naturality_water_fog_color() {
    // Only an exposed column containing the camera admits ocean daylight.
    // Local block lights must not illuminate the entire distant water volume.
    vec4 column = naturality_water_geometry(WaterCamera.xz);
    float exposed = column.w * step(column.y, WaterCamera.y) * step(WaterCamera.y, column.x);
    float daylight = WaterCamera.w * exposed;
    float ambient = min(WaterTime.w, WaterCamera.w);
    float light = WaterSwitches.w != 0 ? 1.0 : clamp(max(ambient, daylight), 0.0, 1.0);
    // No emissive floor: at night, fog extinguishes scenery instead of glowing.
    return WaterTint.rgb * (0.70 * sqrt(light));
}

vec3 naturality_water_terrain_fog(vec3 color, float distance) {
    float range = naturality_water_visibility_range();
    vec3 fog = naturality_water_fog_color();
    // Like End fog, remove texture/light variation first, then dissolve the
    // solid silhouette. Keep the silhouette in the biome's water palette.
    float silhouette = smoothstep(range * 0.25, range * 0.60, distance) * WaterTime.y;
    float fade = smoothstep(range * 0.65, range, distance) * WaterTime.y;
    return mix(mix(color, fog * 0.22, silhouette), fog, fade);
}
#endif
