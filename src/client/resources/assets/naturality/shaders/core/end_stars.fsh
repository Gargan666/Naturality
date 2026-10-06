#version 330
#extension GL_ARB_separate_shader_objects : require
#include <minecraft:dynamictransforms.glsl>
layout(location = 0) flat in float starBrightness;
layout(location = 1) flat in float starSkyHeight;
layout(location = 0) out vec4 fragColor;
void main() {
    float visibility = clamp(ColorModulator.a * 2.0, 0.0, 1.0);
    vec3 tint = vec3(137.0, 82.0, 225.0) / 255.0;
    fragColor = vec4(tint * clamp(starBrightness, 0.0, 1.0), visibility);
}
