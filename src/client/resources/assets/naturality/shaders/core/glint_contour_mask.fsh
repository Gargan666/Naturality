#version 330
#extension GL_ARB_separate_shader_objects : require
#include <minecraft:globals.glsl>
#include <minecraft:dynamictransforms.glsl>
#include <minecraft:fog.glsl>
#include <naturality:pixel_glint.glsl>
uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
layout(location = 0) in vec2 baseUv;
layout(location = 1) in vec3 viewPosition;
layout(location = 2) in vec3 fogPosition;
layout(location = 0) out vec4 fragColor;
void main() {
    if (texture(Sampler0, baseUv).a < 0.1) discard;
    vec3 peak = naturality_glint_palette(Sampler1) * GlintAlpha;
    float fog = total_fog_value(fog_spherical_distance(fogPosition),
        fog_cylindrical_distance(fogPosition), FogEnvironmentalStart,
        FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd);
    if (ColorModulator.x > 0.0) fog = 0.0;
    float depth = ColorModulator.x > 0.0 ? 1.0 + gl_FragCoord.z : max(-viewPosition.z, 0.001);
    // Euclidean camera distance, so the fade is stable when looking around.
    // GUI portraits use their own projection and have no world distance fade.
    float distanceFade = ColorModulator.x > 0.0
        ? 1.0 : 1.0 - smoothstep(0.0, 15.0, length(viewPosition));
    // GLINT blending squares RGB; apply coverage under the square root.
    // Keep depth/occupancy intact even when the outline is fully faded.
    fragColor = vec4(peak * sqrt((1.0 - fog) * distanceFade), depth);
}


