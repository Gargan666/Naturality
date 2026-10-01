#version 330
#extension GL_ARB_separate_shader_objects : require
#include <minecraft:projection.glsl>
#include <minecraft:dynamictransforms.glsl>
layout(location = 0) in vec3 Position;
layout(location = 1) in vec2 UV0;
layout(location = 0) out vec2 baseUv;
layout(location = 1) out vec3 viewPosition;
layout(location = 2) out vec3 fogPosition;
void main() {
    vec4 view = ModelViewMat * vec4(Position, 1.0);
    // Match vanilla's multiplication order for identical replay depth.
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    baseUv = UV0;
    viewPosition = view.xyz;
    fogPosition = Position;
}
