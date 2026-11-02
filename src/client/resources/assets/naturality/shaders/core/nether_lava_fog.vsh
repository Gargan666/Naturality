#version 330
#extension GL_ARB_separate_shader_objects : require
#include <minecraft:projection.glsl>
layout(location = 0) out vec2 texCoord;
layout(location = 1) flat out mat4 inverseProjection;

void main() {
    inverseProjection = inverse(ProjMat);
    texCoord = vec2((gl_VertexIndex << 1) & 2, gl_VertexIndex & 2);
    gl_Position = vec4(texCoord * 2.0 - 1.0, 0.0, 1.0);
}
