#version 330
#extension GL_ARB_separate_shader_objects : require
#include <minecraft:dynamictransforms.glsl>
layout(location = 0) in vec3 skyRay;
layout(location = 1) in vec2 skyUV;
uniform sampler2D Sampler0;
layout(location = 0) out vec4 fragColor;
void main() {
    float height = normalize(skyRay).y;
    // A faint, desaturated horizon glow, with a darker zenith and lower void.
    vec3 horizon = vec3(0.115, 0.065, 0.175);
    vec3 zenith = vec3(0.055, 0.030, 0.090);
    vec3 nadir = vec3(0.070, 0.035, 0.110);
    vec3 color = mix(horizon, height > 0.0 ? zenith : nadir,
        smoothstep(0.0, 0.85, abs(height)));
    // Reuse vanilla's pattern with larger, readable pixels (four tiles per face).
    ivec2 size = textureSize(Sampler0, 0);
    vec3 noise = texelFetch(Sampler0, ivec2(floor(fract(skyUV * 0.25) * vec2(size))), 0).rgb;
    float grain = dot(noise, vec3(0.333333));
    color *= 0.70 + grain * 0.80;
    fragColor = vec4(color * ColorModulator.rgb, 1.0);
}
