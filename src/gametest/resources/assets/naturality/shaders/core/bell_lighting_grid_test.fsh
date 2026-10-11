#version 330
#extension GL_ARB_separate_shader_objects : require
#include <naturality:pixel_lighting.glsl>
layout(location=0) in vec2 texCoord;
layout(location=0) out vec4 fragColor;
void main() {
    // Bell post: 15 UV rows across one full block. Fractional atlas origin
    // also exercises sprite UV shrinking and negative/mirrored U coordinates.
    vec2 texels=vec2(-17.3-texCoord.x*4.0,65.2+texCoord.y*15.0);
    vec3 grid=naturality_texel_lighting_grid(texels/1024.0,vec2(1024.0));
    vec3 lighting=vec3(0.2+0.6*texCoord.y);
    fragColor=vec4(naturality_pixel_lighting_grid(grid,lighting),1.0);
}
