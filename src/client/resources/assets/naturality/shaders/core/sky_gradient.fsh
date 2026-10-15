#version 330
#extension GL_ARB_separate_shader_objects : require
#include <minecraft:fog.glsl>
#include <minecraft:dynamictransforms.glsl>
#include <naturality:sky_dither.glsl>
layout(std140) uniform SkyPalette { vec4 ZenithColor; mat4 SkyViewInverse; };
layout(location = 0) in float sphericalVertexDistance;
layout(location = 1) in float cylindricalVertexDistance;
layout(location = 2) in vec3 skyPosition;
layout(location = 0) out vec4 fragColor;
void main() {
    // Elevation, not the size of the sky fan or render distance, determines
    // the palette. The horizon, original blue and zenith blend over the dome.
    ivec2 cell = naturality_sky_cell(skyPosition);
    float elevation = max(0.0, sin(((float(cell.y) + 0.5) / 2048.0 - 0.25) * 6.28318530718));
    vec3 color = mix(FogColor.rgb, ColorModulator.rgb, naturality_sky_dither(smoothstep(0.0, 0.55, elevation), cell));
    color = mix(color, ZenithColor.rgb, naturality_sky_dither(smoothstep(0.25, 1.0, elevation), cell));
    fragColor = vec4(clamp(color, 0.0, 1.0), 1.0);
}
