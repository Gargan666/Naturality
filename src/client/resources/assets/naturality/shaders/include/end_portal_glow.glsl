// Only bright, alpha=1 texels from the nearest star layer enter this palette. No backdrop, layer
// transparency, or depth-darkened colors are included.
uniform sampler2D Sampler1;

vec4 end_portal_glow_color(vec2 uv, float height, float opacity) {
    // Twice the old length/wave amplitude; preserve the 16px grid and period.
    float slice = floor(uv.x * 16.0);
    float row = floor(uv.y * 16.0);
    float phase = 6.28318530718 * (slice / 24.0 - GameTime * 200.0);
    float reachPixels = floor((1.5 + (3.0 / 16.0) * sin(phase)) * 16.0);
    if (row < 0.0 || row >= reachPixels) return vec4(0.0);
    float surfacePixels = 12.0;
    // Palette progression spans the entire strip, including its submerged half.
    float progress = clamp(row / max(1.0, reachPixels - 1.0), 0.0, 1.0);
    float palette = smoothstep(0.0, 1.0, progress) * 255.0;
    int index = int(floor(palette));
    vec4 tint = mix(texelFetch(Sampler1, ivec2(index, 0), 0),
        texelFetch(Sampler1, ivec2(min(index + 1, 255), 0), 0), fract(palette));
    float alpha = end_portal_side_alpha(height) * (1.0 - smoothstep(surfacePixels, reachPixels, row));
    return vec4(tint.rgb, opacity * alpha * tint.a);
}
