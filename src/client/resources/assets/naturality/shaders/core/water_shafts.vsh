#version 330
#extension GL_ARB_separate_shader_objects : require
#include <minecraft:projection.glsl>
#include <minecraft:dynamictransforms.glsl>
#include <naturality:water.glsl>
#include <naturality:water_sun.glsl>
layout(location = 0) out vec2 shaftUV;
layout(location = 1) out vec3 shaftPosition;
layout(location = 2) flat out float shaftStrength;
layout(location = 3) flat out vec3 shaftShape;

float shaft_random(vec2 cell, float salt) {
    return fract(sin(dot(cell, vec2(127.1, 311.7)) + salt) * 43758.5453);
}

void main() {
    const vec2 corners[6] = vec2[6](vec2(0,0), vec2(0,1), vec2(1,1),
        vec2(0,0), vec2(1,1), vec2(1,0));
    int card = gl_VertexIndex / 6;
    vec2 cell = floor((WaterCamera.xz + vec2(WaterMap.xy)) / 8.0)
        + vec2(card % 9 - 4, card / 9 - 4);
    float seed = shaft_random(cell, 0.0);
    vec2 anchor = (cell + vec2(shaft_random(cell, 13.7), shaft_random(cell, 47.3))) * 8.0 - vec2(WaterMap.xy);
    vec4 column = naturality_water_column(anchor);
    float phase = seed * 6.2831853;
    float reach = mix(12.0, 32.0, shaft_random(cell, 91.2))
        * (1.0 + 0.06 * sin(WaterTime.x * 0.19 + phase));
    float width = mix(3.5, 8.5, shaft_random(cell, 62.8))
        * (1.0 + 0.05 * sin(WaterTime.x * 0.23 + phase));
    shaftShape = vec3(width, reach, phase);
    vec2 toward = WaterCamera.xz - anchor;
    // The captured sky pose is the same source used for the visible sun.
    mat3 sunPose = transpose(mat3(WaterSunInversePose));
    vec3 sun = normalize(sunPose * vec3(0, 1, 0));
    // The vertical card contains the sunlight direction: its edge faces the
    // sun, independently of the camera. At zenith use the solar path tangent.
    vec2 solarTangent = (sunPose * vec3(1, 0, 0)).xz;
    vec2 right = length(sun.xz) > 0.0001 ? normalize(sun.xz) : normalize(solarTangent);
    vec3 beam = -sun;
    shaftUV = corners[gl_VertexIndex % 6];
    shaftPosition = vec3(anchor.x, column.x - 0.03, anchor.y)
        + vec3(right.x, 0, right.y) * ((shaftUV.x - 0.5) * width)
        + beam * (shaftUV.y * reach);
    float rangeFade = 1.0 - smoothstep(20.0, 30.0, length(toward));
    shaftStrength = column.w * naturality_water_open_surface(anchor)
        * rangeFade * smoothstep(1.0, 4.0, column.x - column.y)
        * WaterCamera.w * (1.0 - column.z) * mix(0.4, 1.0, shaft_random(cell, 28.4))
        * (0.92 + 0.08 * sin(WaterTime.x * 0.27 + phase)) * step(0.65, seed)
        * smoothstep(0.0, 0.08, sun.y);
    // Strength is identical for all six vertices. These cards previously
    // generated fragments only to discard every one; clip them as a whole.
    // Keep the exact fragment predicate, with no near-zero visibility cutoff.
    if (shaftStrength <= 0.0) {
        gl_Position = vec4(2.0, 2.0, 2.0, 1.0);
        return;
    }
    gl_Position = ProjMat * ModelViewMat * vec4(shaftPosition - WaterCamera.xyz, 1.0);
}
