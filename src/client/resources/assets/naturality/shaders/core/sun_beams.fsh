#version 330
#extension GL_ARB_separate_shader_objects : require
#include <minecraft:dynamictransforms.glsl>
#include <minecraft:globals.glsl>

layout(location = 0) in vec2 sunPlane;
layout(location = 1) flat in vec2 texelSize;
layout(location = 0) out vec4 fragColor;

void main() {
    const float TAU = 6.28318530718;
    // Sample each cell at its center, on the sun texture's own unrotated grid.
    // Rasterize AFTER rotation so diagonal edges have matching square texels.
    vec2 point = (floor((sunPlane + 30.0) / texelSize) + 0.5) * texelSize - 30.0;
    float clock = TAU * GameTime;
    // Smooth, always counterclockwise, with no discontinuity at GameTime's wrap.
    float rotation = 4.0 * clock + 0.16 * sin(11.0 * clock);
    float coverage = 0.0;
    for (int i = 0; i < 8; i++) {
        float beam = float(i);
        float angle = beam * TAU / 8.0 + rotation;
        vec2 axis = vec2(cos(angle), sin(angle));
        float along = dot(point, axis);
        float across = dot(point, vec2(-axis.y, axis.x));
        float phase = (40.0 + 3.0 * beam) * clock + beam * 1.7
            + 0.7 * sin((7.0 + beam) * clock);
        float reach = mix(22.0, 52.0, 0.5 - 0.5 * cos(phase));
        // Fade over one texel's footprint perpendicular to the rotating edge.
        // Each grid cell keeps one uniform alpha, but gains/loses coverage smoothly.
        vec2 normal = vec2(-axis.y, axis.x);
        float edgePixel = dot(abs(normal), texelSize);
        float side = smoothstep(-0.5 * edgePixel, 0.5 * edgePixel, 3.0 - abs(across));
        if (along >= 0.0 && along <= reach) {
            float tip = 1.0 - smoothstep(0.28, 1.0, along / reach);
            coverage = max(coverage, tip * side);
        }
    }
    if (coverage <= 0.0) discard;
    fragColor = vec4(ColorModulator.rgb, ColorModulator.a * coverage);
}
