#version 330
#extension GL_ARB_separate_shader_objects : require
#include <minecraft:fog.glsl>
#include <minecraft:dynamictransforms.glsl>
#include <minecraft:projection.glsl>
#include <naturality:fire_uniforms.glsl>
layout(location = 0) in vec3 Position;
layout(location = 1) in vec2 UV0;
layout(location = 2) in vec4 Color;
layout(location = 0) out float sphericalVertexDistance;
layout(location = 1) out float cylindricalVertexDistance;
layout(location = 2) out vec2 texCoord0;
layout(location = 3) out vec4 vertexColor;
layout(location = 4) out vec3 firePosition;
layout(location = 5) flat out int fireKind;
void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    sphericalVertexDistance = fog_spherical_distance(Position);
    cylindricalVertexDistance = fog_cylindrical_distance(Position);
    firePosition = Position;
    vertexColor = Color;
    fireKind = 0;
    texCoord0 = vec2(0.0);
    for (int i = 0; i < 4; i++) {
        vec4 b = NaturalityFireBounds[i];
        if (b.x >= 0.0 && all(greaterThanEqual(UV0, b.xy)) && all(lessThanEqual(UV0, b.zw))) {
            fireKind = i;
            texCoord0 = (UV0 - b.xy) / (b.zw - b.xy);
        }
    }
}
