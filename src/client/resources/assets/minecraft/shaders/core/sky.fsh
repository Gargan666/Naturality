#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:fog.glsl>
#include <minecraft:dynamictransforms.glsl>

layout(location = 0) in float sphericalVertexDistance;
layout(location = 1) in float cylindricalVertexDistance;
layout(location = 2) in vec3 skyPosition;
layout(location = 0) out vec4 fragColor;

void main() {
    if (FogColor.a < 0.0) {
        // The negative alpha bypasses terrain fog only. Sky must still meet the
        // clear color at the horizon, in both the main and atmosphere passes.
        // Evaluate distance per fragment to avoid the sky fan's octagonal bands.
        // Finish before its outer edge, with zero slope at both ends.
        float amount = smoothstep(16.0, max(32.0, FogSkyEnd * 0.8), length(skyPosition));
        fragColor = vec4(mix(ColorModulator.rgb, FogColor.rgb, amount), ColorModulator.a);
    } else {
        fragColor = apply_fog(ColorModulator, sphericalVertexDistance, cylindricalVertexDistance,
            0.0, FogSkyEnd, FogSkyEnd, FogSkyEnd, vec4(FogColor.rgb, 1.0));
    }
}
