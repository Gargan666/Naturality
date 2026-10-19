#version 330
#extension GL_ARB_separate_shader_objects : require
#include <naturality:water.glsl>
layout(location = 0) in vec2 shaftUV;
layout(location = 1) in vec3 shaftPosition;
layout(location = 2) flat in float shaftStrength;
layout(location = 3) flat in vec3 shaftShape;
layout(location = 0) out vec4 fragColor;
void main() {
    if (shaftStrength <= 0.0) discard;
    if (naturality_water_view_immersion(shaftPosition - WaterCamera.xyz) < 0.5) discard;
    // Pixel cells follow the sun-aligned card; geometry supplies the beam angle.
    vec2 grid = shaftShape.xy * 4.0;
    vec2 p = (floor(shaftUV * grid) + 0.5) / grid;
    // Keep each spatial texel uniform, but continuously adjust its coverage as
    // the band moves between cells. Neither time nor intensity is rounded.
    float clock = WaterTime.x + shaftShape.z;
    float drift = sin(clock * 0.45 + p.y * 3.0) * 0.05;
    float center = 0.5 + drift;
    float side = 1.0 - smoothstep(0.025, 0.18, abs(p.x - center));
    float tip = 1.0 - smoothstep(0.20, 1.0, p.y);
    float motion = 0.85 + 0.15 * sin(p.y * 24.0 - clock * 1.2 + p.x * 8.0);
    float coverage = side * tip * motion;
    float distance = length(shaftPosition - WaterCamera.xyz);
    float haze = 1.0 - smoothstep(30.0, 44.0, distance);
    float nearFade = smoothstep(0.4, 2.0, distance);
    float alpha = coverage * haze * nearFade * shaftStrength * 0.16;
    fragColor = vec4(mix(vec3(0.85, 0.95, 1.0), WaterTint.rgb, 0.25), alpha);
}
