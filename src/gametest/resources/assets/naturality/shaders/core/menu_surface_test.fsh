#version 330
#extension GL_ARB_separate_shader_objects : require
#include <naturality:menu_region.glsl>
#include <minecraft:fog.glsl>
layout(std140) uniform MenuChecks {
    vec4 Positions[32];
    vec4 Normals[32];
};
layout(location=0) out vec4 fragColor;
void main() {
    int i = int(gl_FragCoord.x);
    float visible = 1.0 - naturality_menu_edge(Positions[i].xyz, Normals[i].xyz);
    float extension = length(naturality_menu_extension(Positions[i].xyz));
    fragColor = vec4(visible, extension / 1024.0,
        naturality_menu_surface_visible(Positions[i].xyz, Normals[i].xyz) ? 1.0 : 0.0, naturality_menu_wall_drop(Positions[i].xyz) / 768.0);
}
