#ifndef NATURALITY_GLASS_SHINE
#define NATURALITY_GLASS_SHINE
#include <naturality:glass_uniforms.glsl>

// Integer hashing gives every line stable, independent placement and width.
uint naturality_glass_line_hash(uint line) {
    uint value = line * 1664525u + 1013904223u;
    value ^= value >> 16;
    value *= 2246822519u;
    value ^= value >> 13;
    return value;
}

vec4 naturality_glass_shine(vec4 color, vec3 light, vec3 dx, vec3 dy) {
    vec3 grid = naturality_texel_grid(texCoord0, TextureSize);
    // Invert the UV Jacobian to measure a whole sprite texel on this face.
    // Capture derivatives before the material/border branches.
    vec2 uvDx = dFdx(texCoord0 * TextureSize), uvDy = dFdy(texCoord0 * TextureSize);
    float determinant = uvDx.x * uvDy.y - uvDx.y * uvDy.x;
    vec3 texelU = vec3(0.0), texelV = vec3(0.0);
    if (abs(determinant) > 1e-12) {
        texelU = (dx * uvDy.y - dy * uvDx.y) / determinant;
        texelV = (dy * uvDx.x - dx * uvDy.x) / determinant;
    }
    vec3 texelLight = naturality_field_at_grid(grid, light);
    vec2 texelLevels = naturality_field_at_grid(grid, vec3(naturalityLightLevels, 0.0)).xy;
    if (naturalityGlassKind < 0) return color;
    vec4 bounds = GlassBounds[naturalityGlassKind];
    vec2 dimensions = GlassDimensions[naturalityGlassKind].xy;
    vec2 localUV = clamp((texCoord0 - bounds.xy) / (bounds.zw - bounds.xy), 0.0, 0.999999);
    ivec2 pixel = ivec2(floor(localUV * dimensions));
    // Exclude exactly the outermost texel at the texture's native resolution.
    if (any(lessThan(pixel, ivec2(1))) || any(greaterThanEqual(pixel, ivec2(dimensions) - 1))) return color;
    vec3 point = naturalityBlockPosition + dx * grid.x + dy * grid.y;
    vec3 relative = naturalityGlassRelative + dx * grid.x + dy * grid.y;
    vec3 n = abs(normalize(cross(dx, dy)));
    vec3 tangent = n.x > n.z && n.x > n.y ? vec3(0, 0, 1) : vec3(1, 0, 0);
    vec3 vertical = n.y > max(n.x, n.z) ? vec3(0, 0, 1) : vec3(0, 1, 0);
    vec3 diagonal = tangent - vertical;
    vec3 view = normalize(-relative);
    float phase = dot(point, diagonal)
        + 0.75 * dot(naturalityGlassForward, diagonal)
        + 0.5 * dot(view, diagonal);
    // Ten lines in sixteen blocks halves the previous density. Section origins
    // are multiples of sixteen, so random placement still joins across sections.
    float periodicPhase = mod(phase, 16.0);
    float cell = floor(periodicPhase / 1.6);
    float edgePixel = max(abs(dot(texelU, diagonal)) + abs(dot(texelV, diagonal)), 1e-6);
    float coverage = 0.0;
    // Jitter can move the closest line into either neighboring cell. Evaluate all
    // three, including wrapped neighbors, to avoid popping at selection boundaries.
    for (int neighbor = -1; neighbor <= 1; neighbor++) {
        float candidate = cell + float(neighbor);
        uint line = uint(mod(candidate, 10.0));
        uint hash = naturality_glass_line_hash(line);
        float placement = float(hash & 65535u) / 65535.0;
        float size = float(hash >> 16) / 65535.0;
        float center = candidate * 1.6 + (placement - 0.5);
        // Retain the mostly-wide mix, with an additional +/-25% width variation.
        float halfWidth = (line == 7u ? 0.052 : 0.104) * mix(0.75, 1.25, size);
        float distanceToLine = abs(periodicPhase - center);
        // Uniform alpha within each sprite texel, with smooth subpixel edges.
        coverage = max(coverage, smoothstep(-0.5 * edgePixel, 0.5 * edgePixel, halfWidth - distanceToLine));
    }
    // Physical level zero is the centre of the first lightmap texel (8 / 256).
    // Keep dim light dim, rather than revealing a uniformly fullbright streak.
    float hasLight = max(texelLevels.x, texelLevels.y) > 8.001 / 256.0 ? 1.0 : 0.0;
    float shine = coverage * hasLight * NATURALITY_GLASS_SHINE_OPACITY;
    vec3 reflection = GlassColors[naturalityGlassKind].rgb * texelLight;
    float alpha = color.a + shine * (1.0 - color.a);
    vec3 rgb = (color.rgb * color.a * (1.0 - shine) + reflection * shine) / max(alpha, 0.00001);
    return vec4(rgb, alpha);
}
#endif
