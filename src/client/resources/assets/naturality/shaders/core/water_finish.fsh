#version 330
#extension GL_ARB_separate_shader_objects : require
#include <minecraft:projection.glsl>
#include <minecraft:dynamictransforms.glsl>
#include <naturality:water.glsl>
uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
uniform sampler2D NaturalityWaterMask;
layout(location = 0) in vec2 texCoord;
layout(location = 0) out vec4 fragColor;
void main() {
    float depth = texture(Sampler1, texCoord).r;
    vec4 water = texture(NaturalityWaterMask, texCoord);
    float distance = 10000.0;
    if (depth > 0.0) {
        #ifndef RENDERPEARL_DEPTH_IS_ZERO_TO_ONE
        depth = depth * 2.0 - 1.0;
        #endif
        vec4 p = inverse(ProjMat) * vec4(texCoord * 2.0 - 1.0, depth, 1.0);
        distance = length(p.xyz / p.w);
    }
    if (water.a > 0.5) distance = min(distance, water.b);
    vec4 color = texture(Sampler0, texCoord);
    fragColor = vec4(color.rgb * (1.0 - naturality_water_distance_fade(distance)), color.a);
}
