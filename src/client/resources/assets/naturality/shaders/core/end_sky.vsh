#version 330
#extension GL_ARB_separate_shader_objects : require
#include <minecraft:projection.glsl>
#include <minecraft:dynamictransforms.glsl>
layout(location = 0) in vec3 Position;
layout(location = 1) in vec2 UV0;
layout(location = 0) out vec3 skyRay;
layout(location = 1) out vec2 skyUV;
void main() {
    skyRay = Position;
    skyUV = UV0;
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
}
