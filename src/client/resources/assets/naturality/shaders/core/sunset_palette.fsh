#version 330
#extension GL_ARB_separate_shader_objects : require
#include <minecraft:dynamictransforms.glsl>
#include <naturality:sky_dither.glsl>
layout(location = 0) in vec3 fanPosition;
layout(location = 1) in vec3 skyDirection;
layout(location = 0) out vec4 fragColor;
void main() {
    // Distance along a ray from the bright fan center to its circular boundary.
    // Unlike interpolated vertex alpha this has no sixteen triangular bands.
    // Share the sky dome's angular grid, independent of the tilted fan mesh.
    ivec2 cell = naturality_sky_cell(skyDirection);
    vec2 delta = fanPosition.xy - vec2(0.0, 100.0);
    float distanceFromCenter = length(delta);
    vec2 direction = delta / max(distanceFromCenter, 0.0001);
    float b = 100.0 * direction.y;
    float edge = -b + sqrt(b*b + 120.0*120.0 - 100.0*100.0);
    float t = clamp(distanceFromCenter / max(edge, 0.001), 0.0, 1.0);
    // End before the polygon boundary so no straight mesh edges remain visible.
    float fade = 1.0 - smoothstep(0.0, 0.94, t);
    vec3 warm = mix(vec3(1.0, 0.47, 0.10), vec3(0.86, 0.23, 0.38), naturality_sky_dither(smoothstep(0.1, 0.85, t), cell));
    float brightness = max(ColorModulator.r, max(ColorModulator.g, ColorModulator.b));
    vec3 color = mix(ColorModulator.rgb, warm * brightness, 0.8);
    float alpha = clamp(ColorModulator.a * fade * 1.12, 0.0, 1.0);
    fragColor = vec4(clamp(color, 0.0, 1.0), naturality_sky_dither(alpha, cell));
}
