// World-anchored 16-pixel ripple slopes. Integer wrapping preserves precision
// across section boundaries and far from the origin; time remains continuous.
vec2 naturality_water_ripple_slope(vec3 position, ivec3 origin) {
    vec2 p = (floor(position.xz * 16.0) + 0.5) / 16.0 + vec2(origin.xz % 64);
    float t = WaterTime.x;
    float a = dot(p, vec2(1.0, 1.0)) * 0.19634954 + t * 0.65;
    float b = dot(p, vec2(1.0, -2.0)) * 0.39269908 - t * 0.48;
    float c = dot(p, vec2(3.0, 1.0)) * 0.78539816 + t * 0.82;
    return vec2(1.0, 1.0) * cos(a) * 0.055
        + vec2(1.0, -2.0) * cos(b) * 0.022
        + vec2(3.0, 1.0) * cos(c) * 0.006;
}
