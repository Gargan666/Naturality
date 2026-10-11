#version 330
#extension GL_ARB_separate_shader_objects : require
#include <minecraft:dynamictransforms.glsl>

layout(location = 0) in vec2 spriteUV;
layout(location = 1) flat in int element;
layout(location = 2) flat in float edgeFade;
layout(location = 0) out vec4 fragColor;

uniform sampler2D Sampler0;

void main() {
    // Native PNG pixels, regardless of filtering settings or framebuffer size.
    ivec2 size = textureSize(Sampler0, 0);
    ivec2 pixel = clamp(ivec2(floor(spriteUV * vec2(size))), ivec2(0), size - 1);
    vec4 sprite = texelFetch(Sampler0, pixel, 0);
    vec2 cell = (vec2(pixel) + 0.5) / vec2(size);
    vec2 point = cell * 2.0 - 1.0;
    float squareRadius = max(abs(point.x), abs(point.y));
    float coverage = sprite.a * dot(sprite.rgb, vec3(0.2126, 0.7152, 0.0722));
    const vec3 DAY[6] = vec3[6](
        vec3(1.0, 0.58, 0.12), vec3(0.12, 0.65, 1.0),
        vec3(0.55, 1.0, 0.18), vec3(1.0, 0.26, 0.10),
        vec3(0.22, 0.90, 0.42), vec3(0.48, 0.82, 0.16));
    const vec3 HORIZON[6] = vec3[6](
        vec3(1.0, 0.37, 0.05), vec3(0.90, 0.20, 0.12),
        vec3(1.0, 0.76, 0.12), vec3(1.0, 0.16, 0.06),
        vec3(1.0, 0.48, 0.10), vec3(0.90, 0.42, 0.10));
    float warmth = ModelViewMat[1][1];
    vec3 tint = mix(DAY[element], HORIZON[element], warmth);
    // Layered chromatic rims, sampled on the same local square pixel grid.
    vec3 rim = mix(vec3(0.22, 0.45, 1.0), vec3(0.95, 0.12, 0.08), warmth);
    if (element == 1 || element == 4 || element == 5)
        tint = mix(tint, rim, 0.65 * smoothstep(0.48, 0.90, squareRadius));
    tint *= mix(vec3(1.0), ColorModulator.rgb, 0.15);
    float alpha = coverage * ColorModulator.a * edgeFade;
    if (alpha <= 0.00001) discard;
    fragColor = vec4(tint, alpha);
}
