#version 330
#extension GL_ARB_separate_shader_objects : require
#include <minecraft:globals.glsl>
#include <naturality:pixel_glint.glsl>
uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
layout(location = 0) in vec2 texCoord;
layout(location = 1) flat in vec3 slotMetadata;
layout(location = 0) out vec4 fragColor;
float spriteAlpha(ivec2 pixel, ivec2 origin, int scale) {
    if (any(lessThan(pixel, ivec2(0))) || any(greaterThanEqual(pixel, ivec2(16)))) return 0.0;
    ivec2 atlasPixel = origin + ivec2(pixel.x, 15 - pixel.y) * scale + ivec2(scale / 2);
    return texelFetch(Sampler0, atlasPixel, 0).a;
}
void main() {
    ivec3 metadata = ivec3(round(slotMetadata * 255.0));
    int scale = max(metadata.b, 1);
    ivec2 origin = ivec2(metadata.r * (16 * scale), textureSize(Sampler0, 0).y - (metadata.g + 1) * (16 * scale));
    vec2 atlasPixel = texCoord * vec2(textureSize(Sampler0, 0));
    ivec2 pixel = ivec2(floor(vec2(atlasPixel.x - float(origin.x), float(origin.y + 16 * scale) - atlasPixel.y) / float(scale)));
    if (spriteAlpha(pixel, origin, scale) > 0.1) discard;
    bool cardinal = spriteAlpha(pixel + ivec2(-1, 0), origin, scale) > 0.1
                 || spriteAlpha(pixel + ivec2(1, 0), origin, scale) > 0.1
                 || spriteAlpha(pixel + ivec2(0, -1), origin, scale) > 0.1
                 || spriteAlpha(pixel + ivec2(0, 1), origin, scale) > 0.1;
    bool diagonal = spriteAlpha(pixel + ivec2(-1, -1), origin, scale) > 0.1
                 || spriteAlpha(pixel + ivec2(1, -1), origin, scale) > 0.1
                 || spriteAlpha(pixel + ivec2(-1, 1), origin, scale) > 0.1
                 || spriteAlpha(pixel + ivec2(1, 1), origin, scale) > 0.1;
    if (!cardinal && !diagonal) discard;
    float easing = cardinal ? 1.0 : 0.25;
    float pulse = 0.5 + 0.5 * cos(GameTime * 400.0 * 6.28318530718);
    float opacity = 1.0 * GlintAlpha * GlintAlpha * pulse * easing;
    vec3 palette = mix(naturality_glint_palette(Sampler1), vec3(1.0), 0.35);
    fragColor = vec4(palette * palette * opacity, opacity);
}


