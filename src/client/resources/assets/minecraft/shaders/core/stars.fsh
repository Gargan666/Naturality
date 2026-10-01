#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:dynamictransforms.glsl>

layout(location = 0) flat in float starBrightness;
layout(location = 1) flat in float starSkyHeight;
layout(location = 0) out vec4 fragColor;

void main() {
    // Fade from the lower-60% boundary (y=+0.2) to lower-40% (y=-0.2).
    // Stars below that boundary are discarded entirely.
    if (starSkyHeight < -0.2) discard;
    float sphereFade = smoothstep(-0.2, 0.2, starSkyHeight);
    // Vanilla caps clear-night star brightness at 0.5, applied to both RGB and
    // alpha. Normalize visibility separately so the brightest peaks reach white.
    float visibility = clamp(ColorModulator.a * 2.0, 0.0, 1.0) * sphereFade;
    fragColor = vec4(vec3(clamp(starBrightness, 0.0, 1.0)), visibility);
}
