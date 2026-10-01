#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:fog.glsl>
#include <minecraft:projection.glsl>
#include <minecraft:dynamictransforms.glsl>

layout(location = 0) in vec3 Position;

layout(location = 0) out vec4 texProj0;
layout(location = 1) out float sphericalVertexDistance;
layout(location = 2) out float cylindricalVertexDistance;
layout(location = 3) out vec3 portalPosition;
layout(location = 4) flat out vec3 portalViewOrigin;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);

    texProj0 = projection_from_position(gl_Position);
    // Block entity vertices are already transformed into camera-relative world axes.
    portalPosition = Position;
    // The projective eye maps to (0, 0, z, 0). Inverting the full transform
    // includes view bob translation, which Minecraft puts in ProjMat.
    vec4 eye = inverse(ProjMat * ModelViewMat) * vec4(0.0, 0.0, 1.0, 0.0);
    portalViewOrigin = eye.xyz / eye.w;
    sphericalVertexDistance = fog_spherical_distance(Position);
    cylindricalVertexDistance = fog_cylindrical_distance(Position);
}
