#version 330
#extension GL_ARB_separate_shader_objects : require
#include <minecraft:dynamictransforms.glsl>
#include <minecraft:projection.glsl>
layout(location = 0) in vec3 Position;
layout(location = 1) in vec4 Color;
layout(std140) uniform SkyPalette { vec4 ZenithColor; mat4 SkyViewInverse; };
layout(location = 0) out vec3 fanPosition;
layout(location = 1) out vec3 skyDirection;
void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    fanPosition = Position;
    // Remove the camera view, retaining the sunset fan's world rotation/scale.
    skyDirection = (SkyViewInverse * ModelViewMat * vec4(Position, 1.0)).xyz;
}
