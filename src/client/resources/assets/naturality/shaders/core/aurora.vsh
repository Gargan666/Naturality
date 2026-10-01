#version 330
#extension GL_ARB_separate_shader_objects : require
#include <minecraft:projection.glsl>
#include <minecraft:dynamictransforms.glsl>
layout(location = 0) in vec3 Position;
layout(location = 1) in vec2 UV0;
layout(location = 2) in vec4 Color;
layout(location = 0) out vec2 curtainUv;
layout(location = 1) flat out vec3 curtainInfo;
layout(location = 2) out vec3 viewPosition;
void main() {
    float strength = clamp(ColorModulator.x, 0.0, 1.0);
    float phaseTime = ColorModulator.y;
    float seed = Color.b * 6.2831853;
    // Every panel endpoint with the same X/family receives the same displacement.
    // The wide panels therefore stay flat, including where a branch joins its trunk.
    float phase = Position.x * .012 + phaseTime + seed;
    float wave = sin(phase) + .35 * sin(Position.x * .027 - phase * .7 + seed);
    vec3 moving = Position + vec3(0.0, wave * mix(1.5, 5.0, strength),
        wave * mix(4.0, 16.0, strength));
    vec4 view = ModelViewMat * vec4(moving, 1.0);
    gl_Position = ProjMat * view;
    // Atmospheric geometry can extend beyond the terrain far plane while retaining real parallax/depth.
    gl_Position.z = max(gl_Position.z, 0.000001 * gl_Position.w);
    viewPosition = view.xyz;
    curtainUv = UV0;
    curtainInfo = Color.rgb;
}
