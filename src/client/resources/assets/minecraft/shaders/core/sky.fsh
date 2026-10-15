#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:fog.glsl>
#include <minecraft:dynamictransforms.glsl>
#include <naturality:sky_dither.glsl>

layout(location = 0) in float sphericalVertexDistance;
layout(location = 1) in float cylindricalVertexDistance;
layout(location = 2) in vec3 skyPosition;
layout(location = 0) out vec4 fragColor;

void main() {
    // Sky always meets the clear color, including when negative alpha bypasses
    // terrain fog. Per-fragment distance avoids the sky fan's octagonal bands.
    float amount = smoothstep(16.0, max(32.0, FogSkyEnd * 0.8), length(skyPosition));
    amount = naturality_sky_dither(amount, naturality_sky_cell(skyPosition));
    fragColor = vec4(mix(ColorModulator.rgb, FogColor.rgb, amount), ColorModulator.a);
}
