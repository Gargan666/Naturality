#ifndef NATURALITY_PIXEL_GLINT
#define NATURALITY_PIXEL_GLINT

vec3 naturality_glint_palette(sampler2D paletteTexture) {
    // Sample a fixed distributed palette from the active pack's glint texture.
    // Brightness weighting preserves its brighter colors without carrying its
    // old moving line pattern onto the new procedural bands.
    vec3 sum = vec3(0.0);
    float weight = 0.0;
    ivec2 size = textureSize(paletteTexture, 0);
    for (int y = 0; y < 4; ++y) {
        for (int x = 0; x < 4; ++x) {
            ivec2 at = min(ivec2((vec2(x, y) + 0.5) * vec2(size) / 4.0), size - 1);
            vec4 sampleColor = texelFetch(paletteTexture, at, 0);
            float w = max(sampleColor.r, max(sampleColor.g, sampleColor.b)) * sampleColor.a;
            sum += sampleColor.rgb * sampleColor.a * w;
            weight += w;
        }
    }
    return sum / max(weight, 0.00001);
}

// Base UVs are atlas UVs for items and texture UVs for armor. In either case,
// multiplying by the BASE texture dimensions gives the actual texel grid.
// Atlas sprite origins are integer texels, so no per-sprite lookup is needed.
vec4 naturality_pixel_glint(sampler2D baseTexture, sampler2D paletteTexture, vec2 uv) {
    vec2 pixel = floor(uv * vec2(textureSize(baseTexture, 0))) + 0.5;
    float time = GameTime * 1200.0;
    // UV axes point right/down. Sampling p - velocity*t with negative X/Y
    // velocity moves the whole grid up-left in the sprite's own orientation.
    vec2 movingPixel = pixel + vec2(time * 6.0);
    // Project onto perpendicular axes rotated 15 degrees: two equally bright
    // families of parallel lines form a square grid with 24-texel spacing.
    const float COS_15 = 0.9659258263;
    const float SIN_15 = 0.2588190451;
    vec2 grid = vec2(dot(movingPixel, vec2(COS_15, SIN_15)),
                     dot(movingPixel, vec2(-SIN_15, COS_15))) / 24.0;
    vec2 distanceToLine = abs(fract(grid + 0.5) - 0.5);
    // Linear borders, quantized to 20% coverage increments, make the fade
    // visibly stepped while retaining exactly one value per source texel.
    vec2 bands = clamp((vec2(0.22) - distanceToLine) / 0.18, 0.0, 1.0);
    float coverage = floor(max(bands.x, bands.y) * 5.0 + 0.5) / 5.0;

    vec3 tint = naturality_glint_palette(paletteTexture);
    // Vanilla squares RGB when adding glint. Apply 90% peak opacity inside
    // the square root to preserve that contribution after compositing.
    return vec4(tint * sqrt(coverage * 0.90), 1.0);
}

#endif
