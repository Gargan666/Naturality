// Ordered gradient coverage in sky-space cells, never framebuffer coordinates.
// One turn contains 2048 cells (~0.176 degrees each), so zoom magnifies them.
ivec2 naturality_sky_cell(vec3 position) {
    vec3 direction = normalize(position);
    vec2 angular = vec2(atan(direction.x, direction.z), asin(clamp(direction.y, -1.0, 1.0)));
    return ivec2(floor((angular / 6.28318530718 + vec2(0.5, 0.25)) * 2048.0));
}
float naturality_sky_dither(float amount, ivec2 cell) {
    const float bayer[16] = float[16](
        0.0, 8.0, 2.0, 10.0,
        12.0, 4.0, 14.0, 6.0,
        3.0, 11.0, 1.0, 9.0,
        15.0, 7.0, 13.0, 5.0);
    ivec2 p = cell & ivec2(3);
    float threshold = (bayer[p.x + p.y * 4] + 0.5) / 16.0;
    // Select neighboring gradient levels instead of adding noise to final RGB.
    return clamp(floor(clamp(amount, 0.0, 1.0) * 32.0 + threshold) / 32.0, 0.0, 1.0);
}
