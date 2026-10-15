#version 330
#extension GL_ARB_separate_shader_objects : require
#include <naturality:texture_lod.glsl>
uniform sampler2D Sampler0;
layout(std140) uniform LodTest {
    vec4 Bounds;
    vec4 Sprite;
    vec4 Size;
};
layout(location=0) out vec4 fragColor;
void main() {
    float row = floor(gl_FragCoord.y);
    vec2 pixel = vec2(mod(row, Size.x), floor(row / Size.x));
    vec2 uv = mix(Sprite.xy, Sprite.zw, (pixel + 0.5) / Size.xy);
    float distanceToCamera = 128.0 + (gl_FragCoord.x - 0.5) * 0.5;
    float weight = naturality_texture_lod_weight(uv, Bounds, distanceToCamera);
    fragColor = mix(textureLod(Sampler0, uv, 0.0),
        textureLod(Sampler0, naturality_texture_average_uv(uv, Bounds), 0.0), weight);
}
