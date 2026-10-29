#ifndef NATURALITY_PROCEDURAL_FIRE
#define NATURALITY_PROCEDURAL_FIRE
#include <naturality:fire_uniforms.glsl>
uniform sampler2D NaturalityFirePalette;
#include <naturality:feature_settings.glsl>
#ifndef NATURALITY_SODIUM
uniform sampler2D NaturalityFireHeat;
#endif

// Derive a face's physical dimensions before quantizing its UVs. A four-pixel-wide
// post gets four new pixels, rather than a squeezed sixteen-pixel sprite.
vec2 naturality_fire_resolution(vec3 p, vec2 uv) {
    vec2 a = dFdx(uv), b = dFdy(uv);
    float det = a.x * b.y - a.y * b.x;
    if (abs(det) < 1e-10) return vec2(16.0);
    vec3 du = (dFdx(p) * b.y - dFdy(p) * a.y) / det;
    vec3 dv = (dFdy(p) * a.x - dFdx(p) * b.x) / det;
    return clamp(round(vec2(length(du), length(dv)) * 16.0), vec2(1.0), vec2(256.0));
}

// One bounded simulation strip serves every fire. Interpolate heat, not RGB or
// binary sprite opacity; the final lookup always returns an authored color.
float naturality_heat(vec2 source) {
    ivec2 cell = ivec2(floor(source));
    vec2 f = fract(source);
    vec2 values[4];
    for (int i = 0; i < 4; i++) {
        ivec2 p = cell + ivec2(i & 1, i >> 1);
        p.x = (p.x % 256 + 256) % 256;
        p.y = clamp(p.y, 0, 79);
        values[i] = texelFetch(NaturalityFirePalette, ivec2(p.x, 520 + p.y), 0).rg;
    }
    vec2 heat = mix(mix(values[0], values[1], f.x), mix(values[2], values[3], f.x), f.y);
    return mix(heat.x, heat.y, float(NaturalityFireInfo.w) / 65535.0);
}

vec4 naturality_heat_color(int kind, float heat) {
    if (heat < 0.5) return vec4(0.0);
    return texelFetch(NaturalityFirePalette, ivec2(int(round(clamp(heat, 0.0, 1.0) * 255.0)), 512 + kind), 0);
}

float naturality_fire_profile(int kind, float rawHeat, float v) {
    // Measured from all 32 vanilla frames: height-dependent coverage and color
    // distribution, sampled continuously in heat/height before palette selection.
    vec2 pixel = vec2(clamp(rawHeat, 0.0, 1.0) * 255.0,
        600.0 + float(kind * 16) + clamp(v * 16.0 - 0.5, 0.0, 15.0));
    return textureLod(NaturalityFirePalette, (pixel + 0.5) / vec2(textureSize(NaturalityFirePalette, 0)), 0.0).r;
}

float naturality_fire_heat_patch(int kind, vec2 point, float phase) {
    // Four hidden logical fuel rows. Native 16x16 cell centers land on the
    // original simulation lattice, irrespective of the GPU reconstruction size.
    float raw = naturality_heat(vec2(point.x * 64.0 - 2.0 + phase, 78.0 - point.y * 64.0));
    return naturality_fire_profile(kind, raw, point.y);
}

vec4 naturality_grouped_fire(int kind, vec2 uv, vec2 resolution, vec3 position, vec2 shape, vec3 seed) {
    vec2 point = (floor(uv * resolution) + 0.5) / resolution;
    vec2 a = dFdx(uv), b = dFdy(uv);
    float det = a.x * b.y - a.y * b.x;
    vec2 profile = shape;
    vec3 samplePosition = position;
    if (abs(det) > 1e-10) {
        vec2 du = (dFdx(shape) * b.y - dFdy(shape) * a.y) / det;
        vec2 dv = (dFdy(shape) * a.x - dFdx(shape) * b.x) / det;
        profile += du * (point.x - uv.x) + dv * (point.y - uv.y);
        vec3 positionU = (dFdx(position) * b.y - dFdy(position) * a.y) / det;
        vec3 positionV = (dFdy(position) * a.x - dFdx(position) * b.x) / det;
        samplePosition += positionU * (point.x - uv.x) + positionV * (point.y - uv.y);
    }
    float height = 12.0 - point.y * 16.0;
    if (height < profile.x || height >= profile.y) return vec4(0.0);
    int flags = int(round(seed.r * 255.0));
    int plane = flags & 3;
    float edge = 1.0;
    if ((flags & 16) == 0) edge *= smoothstep(0.0, 0.35, point.x);
    if ((flags & 32) == 0) edge *= smoothstep(0.0, 0.35, 1.0 - point.x);
    // Contract the entire heat column, including its bright root, before
    // sampling. Subtracting heat alone left a rectangular bright cut at the foot.
    profile.y = mix(profile.x, profile.y, edge);
    if (height >= profile.y) return vec4(0.0);
    // Only the tangent coordinate is shared. Perpendicular planes and separate
    // rows sample different regions of the evolving field, never x+z mirrors.
    bool outer = (flags & 4) != 0;
    float across = outer ? samplePosition.x + samplePosition.z : (plane < 2 ? samplePosition.x : samplePosition.z);
    float vertical = 1.0 - (height - profile.x) / max(profile.y - profile.x, 0.0625);
    float phase = float(((flags >> 6) * 13 + (outer ? 0 : plane * 9) + kind * 17) * 4);
    float extent = max(profile.y - profile.x, 1.0);
    float widen = outer ? 0.0 : smoothstep(1.4, 5.375, extent);
    float sourceY = 78.0 - vertical * 64.0;
    // Broad roots feed progressively smaller tongues higher up. These fields
    // have section-periodic coordinates, so a mesh boundary cannot cut a patch.
    float breakup = widen * smoothstep(0.15, 0.8, 1.0 - vertical);
    float fine = naturality_heat(vec2(across * 64.0 - 2.0 + phase, sourceY));
    if (breakup > 0.0) fine = mix(fine, naturality_heat(vec2(across * 128.0 - 2.0 + phase, sourceY)), breakup);
    float broad = widen * (1.0 - breakup * 0.85);
    float raw = fine;
    if (broad > 0.0) raw = mix(fine, naturality_heat(vec2(across * 16.0 - 2.0 + phase, sourceY)), broad);
    float heat = naturality_fire_profile(kind, raw, vertical);
    // Unmatched planes end in tongues instead of cutting a hot rectangle at the
    // boundary. Matching neighbours use the same heat and retain their join.
    heat -= (1.0 - edge) * 0.65 + breakup * max(0.0, 0.55 - fine) * 0.16;
    if (heat < 0.5) return vec4(0.0);
    // Broad cool regions retain their orange silhouette while their bright core
    // stays lower. The continuous eight-block field agrees across mesh sections.
    float cooler = 0.5 + 0.5 * sin(across * 0.78539816339 + phase * 0.13);
    float raisedCore = widen * 0.22 * smoothstep(0.58, 0.88, heat) * smoothstep(0.15, 0.55, vertical);
    float colorHeat = max(0.5, heat + raisedCore - (1.0 - edge) * 0.25
        - widen * cooler * (0.03 + 0.09 * (1.0 - vertical)));
    return naturality_heat_color(kind, colorHeat);
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
    int mask = packed >> 20;
    float phase = float(((key.x * 17 + key.y * 31 + key.z * 43 + kind * 17) % 64) * 4);
    if (mask == 0) return naturality_heat_color(kind, naturality_fire_heat_patch(kind, point, phase));
    ivec3 origin = ivec3(packed & 31, (packed >> 5) & 31, (packed >> 10) & 31);
    // Compatible wall faces share horizontal coordinates. At a vertical join,
    // interpolate to a common heat slice so neither tile restarts a white base.
    float heat = naturality_fire_heat_patch(kind, vec2(float(origin.x) + point.x, point.y),
        float(origin.z * 37 + kind * 71));
    float seam = 0.0;
    if ((mask & 4) != 0) seam = max(seam, 1.0 - smoothstep(0.0, .25, point.y));
    if ((mask & 8) != 0) seam = max(seam, 1.0 - smoothstep(0.0, .25, 1.0 - point.y));
    // Preserve vertical texture variation near the join instead of stretching
    // one constant middle row into a bright horizontal band.
    float seamV = .15 + min(point.y, 1.0 - point.y);
    float seamHeat = naturality_fire_heat_patch(kind, vec2(float(origin.x) + point.x, seamV),
        float(origin.z * 37 + kind * 71));
    return naturality_heat_color(kind, mix(heat, seamHeat, seam));
}
#endif
