#version 330
#extension GL_ARB_separate_shader_objects : require
#include <minecraft:projection.glsl>
#include <minecraft:dynamictransforms.glsl>
#include <minecraft:globals.glsl>
uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
uniform sampler2D Sampler2;
layout(location = 0) in vec2 texCoord;
layout(location = 0) out vec4 fragColor;

void findNearest(ivec2 cell, ivec2 size, int sampleStep, out vec4 nearest, out int distanceToShape) {
    nearest = vec4(0.0);
    distanceToShape = 4;
    // Keep contour thickness constant while moving its sample center on a
    // 4-pixel source grid. Output cell size controls the visible rasterization.
    for (int y = -3; y <= 3; ++y) for (int x = -3; x <= 3; ++x) {
        ivec2 at = cell + ivec2(x, y) * sampleStep;
        if (any(lessThan(at, ivec2(0))) || any(greaterThanEqual(at, size))) continue;
        vec4 candidate = texelFetch(Sampler0, at, 0);
        if (candidate.a <= 0.0) continue;
        int d = max(abs(x), abs(y));
        bool closer = nearest.a == 0.0 || (ColorModulator.x > 0.0 ? candidate.a > nearest.a : candidate.a < nearest.a);
        if (d < distanceToShape || (d == distanceToShape && closer)) {
            nearest = candidate;
            distanceToShape = d;
        }
    }
}

void main() {
    ivec2 size = textureSize(Sampler0, 0);
    ivec2 pixel = ivec2(gl_FragCoord.xy);
    if (texelFetch(Sampler0, pixel, 0).a > 0.0) discard;

    // Player previews use 3-pixel cells (four times the 12-pixel hand raster).
    // First-person hands remain fixed; world items and armor vary by distance.
    int initialCellSize = ColorModulator.x > 0.0 ? 3 : 12;
    int initialSampleStep = ColorModulator.x > 0.0 ? 1 : 4;
    ivec2 coarseCell = (pixel / initialCellSize) * initialCellSize + ivec2(initialCellSize / 2);
    vec4 nearest;
    int distanceToShape;
    findNearest(coarseCell, size, initialSampleStep, nearest, distanceToShape);
    if (nearest.a <= 0.0) discard;

    if (ColorModulator.x <= 0.0 && ColorModulator.y <= 0.0) {
        float rasterSize = 12.0 / (1.0 + pow(nearest.a / 3.0, 1.5));
        int cellSize = max(1, int(floor(rasterSize + 0.5)));
        ivec2 cell = (pixel / cellSize) * cellSize + ivec2(cellSize / 2);
        if (cellSize != 12) {
            findNearest(cell, size, max(1, int(floor(float(cellSize) / 3.0 + 0.5))), nearest, distanceToShape);
            if (nearest.a <= 0.0) discard;
        }
    }

    float depth = texelFetch(Sampler2, pixel, 0).r;
    if (ColorModulator.x > 0.0) {
        if (depth > nearest.a - 1.0 + 0.00001) discard;
    } else if (depth > 0.0) {
#ifndef RENDERPEARL_DEPTH_IS_ZERO_TO_ONE
        depth = depth * 2.0 - 1.0;
#endif
        vec4 scene = inverse(ProjMat) * vec4(texCoord * 2.0 - 1.0, depth, 1.0);
        if (-scene.z / scene.w < nearest.a - 0.001) discard;
    }
    float easing = distanceToShape <= 2 ? 1.0 : 0.25;
    float pulse = (0.5 + 0.5 * cos(GameTime * 400.0 * 6.28318530718)) * easing;
    if (ColorModulator.x > 0.0)
        fragColor = vec4(nearest.rgb * nearest.rgb * pulse, GlintAlpha * GlintAlpha * pulse);
    else fragColor = vec4(nearest.rgb * sqrt(pulse), 1.0);
}








