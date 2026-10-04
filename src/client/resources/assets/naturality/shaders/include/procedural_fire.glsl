#ifndef NATURALITY_PROCEDURAL_FIRE
#define NATURALITY_PROCEDURAL_FIRE
#include <naturality:fire_uniforms.glsl>
#include <naturality:feature_settings.glsl>
#ifndef NATURALITY_SODIUM
uniform sampler2D NaturalityFireHeat;
#endif
uniform sampler2D NaturalityFirePalette;

// Derive a face's physical dimensions before quantizing its UVs. A four-pixel-wide
// post gets four new pixels, rather than a squeezed sixteen-pixel sprite.
vec2 naturality_fire_resolution(vec3 p, vec2 uv) {
    vec2 a = dFdx(uv), b = dFdy(uv);
    float det = a.x * b.y - a.y * b.x;
    if (abs(det) < 1e-10) return vec2(16.0);
    vec3 du = (dFdx(p) * b.y - dFdy(p) * a.y) / det;
    vec3 dv = (dFdy(p) * a.x - dFdx(p) * b.x) / det;
    return clamp(round(vec2(length(du), length(dv)) * 16.0), vec2(1.0), vec2(128.0));
}

vec4 naturality_fire_patch(int kind, int tick, ivec3 anchor, vec2 point) {
    anchor = (anchor % 32 + 32) % 32;
    int phase = (anchor.x * 13 + anchor.y * 7 + anchor.z * 19) % 32;
    int frame = (tick + phase + (kind % 2 == 0 ? 16 : 0)) % 32;
    ivec2 pixel = clamp(ivec2(floor(point * 16.0)), ivec2(0), ivec2(15));
    return texelFetch(NaturalityFirePalette, ivec2(kind * 16 + pixel.x, frame * 16 + pixel.y), 0);
}

vec4 naturality_connected_fire(int kind, int tick, int packed, vec2 point) {
    int mask = (packed >> 20) & 15;
    ivec3 origin = ivec3(packed & 31, (packed >> 5) & 31, (packed >> 10) & 31);
    bool left = (mask & 1) != 0 && point.x < 0.25;
    bool right = (mask & 2) != 0 && point.x > 0.75;
    bool up = (mask & 4) != 0 && point.y < 0.25;
    bool down = (mask & 8) != 0 && point.y > 0.75;
    vec4 base = naturality_fire_patch(kind, tick, origin, point);
    if (!(left || right || up || down)) return base;

    // Overlap vanilla exemplars at shared edges, using the same spatial seed and
    // consecutive pixel columns/rows on both sides. Corner patches meet four tiles.
    ivec3 anchor = origin;
    vec2 samplePoint = point;
    float weight = 0.0;
    if (left || right) {
        anchor.x += right ? 1 : 0;
        samplePoint.x = 0.5 + point.x - (right ? 1.0 : 0.0);
        weight = max(weight, 1.0 - min(point.x, 1.0 - point.x) * 4.0);
    }
    if (up || down) {
        anchor.y += up ? 1 : 0;
        samplePoint.y = 0.5 + point.y - (down ? 1.0 : 0.0);
        weight = max(weight, 1.0 - min(point.y, 1.0 - point.y) * 4.0);
    }
    // Static spatial quilting preserves exact vanilla colors (no RGB crossfade)
    // and cannot introduce random changes between animation frames.
    ivec2 pixel = ivec2(floor(point * 16.0));
    int noise = (pixel.x * 3 + pixel.y * 5 + origin.x * 7 + origin.y * 11) & 15;
    if (weight > 0.8 || float(noise) / 16.0 < weight)
        return naturality_fire_patch(kind, tick, anchor, samplePoint);
    return base;
}

vec4 naturality_fire_color(int kind, vec2 uv, vec2 resolution, vec3 seed) {
    // Rasterize the complete exemplar domain on this face's physical pixel grid.
    vec2 cell = floor(clamp(uv, 0.0, 0.999999) * resolution);
    vec2 point = (cell + 0.5) / resolution;
    ivec3 key = ivec3(round(seed * 255.0));
    #ifdef NATURALITY_SODIUM
    int tick = NaturalityFireInfo.z;
    #else
    int tick = int(round(texelFetch(NaturalityFireHeat, ivec2(0), 0).b * 255.0));
    #endif
    if (!NATURALITY_FIRE_VARIATION) {
        ivec2 pixel = clamp(ivec2(floor(point * 16.0)), ivec2(0), ivec2(15));
        int frame = (tick + (kind % 2 == 0 ? 16 : 0)) % 32;
        return texelFetch(NaturalityFirePalette, ivec2(kind * 16 + pixel.x, frame * 16 + pixel.y), 0);
    }
    int packed = (key.x << 16) | (key.y << 8) | key.z;
    if ((packed >> 20) != 0) return naturality_connected_fire(kind, tick, packed, point);
    int frame = (tick + key.x + key.y * 7 + key.z * 13 + (kind % 2 == 0 ? 16 : 0)) % 32;
    ivec2 source = clamp(ivec2(floor(point * 16.0)), ivec2(0), ivec2(15));
    ivec2 address = ivec2(kind * 16 + source.x, frame * 16 + source.y);
    vec4 color = texelFetch(NaturalityFirePalette, address, 0);

    // At most one of the 256 source cells may vary, and only by borrowing a
    // similar opaque neighbour. Thus >=99.6% of every canonical frame is exact
    // vanilla, with its original silhouette and bright pockets preserved.
    int candidate = (key.x * 17 + key.y * 31 + key.z * 43) % 256;
    if (source.y * 16 + source.x == candidate && color.a > 0.5) {
        int direction = key.z % 2 == 0 ? 1 : -1;
        ivec2 neighbour = ivec2(kind * 16 + clamp(source.x + direction, 0, 15), address.y);
        vec4 alternative = texelFetch(NaturalityFirePalette, neighbour, 0);
        if (alternative.a > 0.5 && length(alternative.rgb - color.rgb) < 0.18)
            color = alternative;
    }
    return color;
}
#endif
