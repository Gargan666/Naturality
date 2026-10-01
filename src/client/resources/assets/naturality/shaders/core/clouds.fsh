#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:fog.glsl>
#include <minecraft:oit.glsl>

layout(location = 0) in float vertexDistance;
layout(location = 1) in vec4 vertexColor;
layout(location = 2) in vec2 fadePosition;
layout(location = 3) flat in float fadeRows;
layout(location = 4) flat in vec2 capWeights;

#ifndef OIT_ALPHA_ONLY
layout(location = 0) out vec4 fragColor;
#endif

vec4 calculateFinalColor(vec4 color) {
    #ifdef OIT_ACCUMULATE
    color = sampleColorForAccumulation(color);
    #endif
    return color;
}

void main() {
    vec4 color = vertexColor;
    if (fadeRows >= 2.0) {
        // Only gradient side walls are one-sided. Horizontal planes have fadeRows = 0
        // and remain visible from both directions, including the cloud bottom.
        if (!gl_FrontFacing) discard;
        // Square, surface-aligned pixels; each row has a discrete opacity, including zero at the top.
        float rows = max(fadeRows, 3.0);
        vec2 pixel = floor(fadePosition * rows);
        float row = clamp(pixel.y, 0.0, rows - 1.0);
        float checker = mod(pixel.x + pixel.y, 2.0) * 0.3;
        float upward = row == 0.0 ? 1.0 : (row >= rows - 1.0 ? 0.0 : 1.0 - (row + checker) / (rows - 1.0));
        float downward = row >= rows - 1.0 ? 1.0 : (row == 0.0 ? 0.0 : (row - checker) / (rows - 1.0));
        // Normalize the symmetric tent so even pixel counts still reach full color.
        float center = (rows - 1.0) * 0.5;
        float peak = floor(center);
        float middle = clamp((center - abs(row - center)) / max(peak, 1.0), 0.0, 1.0);
        float fade = capWeights.x * upward + capWeights.y * downward
            + (1.0 - capWeights.x - capWeights.y) * middle;
        color.a *= fade;
    }
    if (color.a <= 0.0) discard;
    #ifndef OIT_DEPTH_BOUNDS
    color.a *= 1.0f - linear_fog_value(vertexDistance, 0, FogCloudsEnd);
    if (color.a <= 0.0) discard;
    #endif

    #ifdef OIT_ALPHA_ONLY
    executeAlphaOnlyPhase(gl_FragCoord.z, color.a);
    #else
    fragColor = calculateFinalColor(color);
    #endif
}
