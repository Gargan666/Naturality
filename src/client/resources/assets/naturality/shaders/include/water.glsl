#ifndef NATURALITY_WATER_GLSL
#define NATURALITY_WATER_GLSL
layout(std140) uniform NaturalityWater {
    ivec4 WaterMap; // origin X/Z, world minimum Y, enabled
    vec4 WaterCamera; // map-relative X/Z, world Y, sunlight
    vec4 WaterParams; // black depth, clear depth, refraction pixel size, render distance
    ivec4 WaterSwitches; // depth, distortion, shimmer, night vision
    vec4 WaterTime; // seconds, immersed, camera water depth, local illumination
    vec4 WaterTint; // biome-blended current water color, camera turbidity
    ivec4 WaterEnvironment; // Overworld atmosphere, falling water, float bits of rain fog strength, reserved
};
uniform sampler2D NaturalityWaterColumns;

float naturality_water_distance_fade(float distance) {
    float end = max(14.0, 104.0 / (1.0 + max(WaterTime.z, 0.0) * 0.065));
    end = min(end, max(12.0, WaterParams.w * 0.65));
    end *= mix(1.0, 0.45, WaterTint.a);
    return smoothstep(end * 0.3, end, distance) * WaterTime.y;
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
vec4 naturality_water_column(vec2 position) {
    ivec2 cell = ivec2(floor(position));
    if (WaterMap.w == 0 || any(lessThan(cell, ivec2(0))) || any(greaterThanEqual(cell, ivec2(256)))) return vec4(0);
    ivec4 data = ivec4(round(texelFetch(NaturalityWaterColumns, cell, 0) * 255.0));
    float top = float(WaterMap.z + data.r + data.g * 256) + 0.875;
    return vec4(top, top - float(data.b) + 0.125, naturality_water_murk(position), data.a > 0 ? 1.0 : 0.0);
}
float naturality_water_depth(vec3 p, vec4 column) {
    return column.w > 0.0 && p.y >= column.y - 0.08 ? max(column.x - p.y, 0.0) : 0.0;
}
float naturality_water_daylight(float depth, float murk) {
    if (WaterSwitches.x == 0 || WaterSwitches.w != 0) return 1.0;
    return 1.0 - smoothstep(mix(WaterParams.y, 0.0, murk), mix(WaterParams.x, 8.0, murk), depth);
}
#endif
