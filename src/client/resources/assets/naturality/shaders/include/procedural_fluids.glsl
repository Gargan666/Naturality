#include <naturality:fluid_uniforms.glsl>
uniform sampler2D NaturalityFluidHeat;
uniform sampler2D NaturalityLavaPalette;

// Integer hashing avoids floating-point loss at the world border. No small spatial period.
uvec2 naturality_fluid_hash(ivec2 cell) {
    uvec2 h = uvec2(cell);
    h = h * uvec2(1597334677u, 3812015801u);
    h ^= h.yx >> 16u;
    h *= uvec2(2246822519u, 3266489917u);
    return h ^ (h.yx >> 13u);
}

vec4 naturality_fluid_color(int kind, vec3 position, ivec3 origin, vec2 localUV, vec3 cornerFlow) {
    vec3 normal = abs(cross(dFdx(position), dFdy(position)));
    bool topSurface = normal.y >= max(normal.x, normal.z);
    vec2 p;
    ivec2 base;
    if (topSurface) {
        p = position.xz;
        base = origin.xz;
    } else if (normal.x > normal.z) {
        p = vec2(position.z, -position.y);
        base = ivec2(origin.z, -origin.y);
    } else {
        p = vec2(position.x, -position.y);
        base = ivec2(origin.x, -origin.y);
    }

    float spritePixels = (kind == 1 || kind == 3) ? 32.0 : 16.0;
    vec2 dx = dFdx(p), dy = dFdy(p);
    vec2 tx = dFdx(localUV) * spritePixels / 64.0;
    vec2 ty = dFdy(localUV) * spritePixels / 64.0;
    float determinant = dx.x * dy.y - dx.y * dy.x;
    // Reconstruct vanilla's flow orientation without its per-block UV reset.
    mat2 mapping = mat2(0.25, 0.0, 0.0, 0.25);
    if (abs(determinant) > 1e-12) {
        mapping = mat2((tx * dy.y - ty * dx.y) / determinant,
                       (ty * dx.x - tx * dy.x) / determinant);
    }
    vec2 pixel = (floor(p * 16.0 + 0.0001) + 0.5) / 16.0;
    if (topSurface && cornerFlow.z > 0.99 && (kind == 1 || kind == 3)) {
        // Evaluate the shared corner velocity at this world pixel's center.
        // Both sides of a block edge interpolate the same endpoint vectors.
        vec2 flow = cornerFlow.xy;
        if (abs(determinant) > 1e-12) {
            vec2 fx = dFdx(flow), fy = dFdy(flow);
            mat2 gradient = mat2((fx * dy.y - fy * dx.y) / determinant,
                                 (fy * dx.x - fx * dy.x) / determinant);
            flow += gradient * (pixel - p);
        }
        float speed = length(flow);
        if (speed > 0.001) {
            flow /= speed;
            mapping = mat2(flow.y, flow.x, -flow.x, flow.y) * 0.25;
        }
    }
    // Lava's spatial assembly is one 16x16 block wide, not a two-block cloud.
    float patchSize = kind >= 2 ? 1.0 : 2.0;
    vec2 grid = pixel / patchSize;
    ivec2 cell = ivec2(floor(grid));
    vec2 fraction = fract(grid);
    vec2 weight = fraction * fraction * (3.0 - 2.0 * fraction);
    float heat = 0.0;
    // A shared lattice and smooth weights join across block/section boundaries.
    // Blend heat before the nonlinear palette, preserving the classic color curve.
    for (int y = 0; y <= 1; y++) for (int x = 0; x <= 1; x++) {
        ivec2 corner = cell + ivec2(x, y);
        uvec2 hash = naturality_fluid_hash(base / int(patchSize) + corner);
        // Diagonal flow can cancel U/V terms exactly onto a texel boundary.
        // Derivative roundoff then picked different neighbors inside ONE surface
        // pixel, producing depth-fighting-like speckles. Resolve the virtual texel
        // once, with a small numerical tolerance, and sample its center.
        vec2 texel = floor(mapping * (pixel - vec2(corner) * patchSize) * 64.0 + 0.01);
        vec2 sampleUV = (texel + vec2(hash & uvec2(63u)) + 0.5) / 64.0;
        float w = (x == 0 ? 1.0 - weight.x : weight.x) * (y == 0 ? 1.0 - weight.y : weight.y);
        heat += textureGrad(NaturalityFluidHeat, sampleUV, tx, ty)[kind] * w;
    }
    heat = clamp(heat, 0.0, 1.0);
    float squared = heat * heat;
    if (kind >= 2) {
        // The source palette contains all animation pixels, including a handful
        // of exceptionally bright outliers. A bounded smoothstep saturated to
        // that rare maximum over whole hot patches. Logistic tails retain local
        // contrast while approaching those rare colors gradually, without a
        // finite heat threshold that turns an entire highlight into the maximum.
        float paletteHeat = 1.0 / (1.0 + exp(-24.0 * (heat - 0.46)));
        int count = kind == 2 ? NaturalityFluidInfo.x : NaturalityFluidInfo.y;
        int index = int(round(paletteHeat * float(count - 1)));
        return vec4(texelFetch(NaturalityLavaPalette,
            ivec2(index % 256, index / 256 + (kind - 2) * NaturalityFluidInfo.z), 0).rgb, 1.0);
    }
    // The historical fixed blue is supplied by modern biome tint instead.
    return vec4(vec3((50.0 + 64.0 * squared) / 114.0), (146.0 + 50.0 * squared) / 255.0);
}
