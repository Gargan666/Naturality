#version 330
#extension GL_ARB_separate_shader_objects : require
#include <minecraft:projection.glsl>
#include <minecraft:dynamictransforms.glsl>
#include <minecraft:globals.glsl>
#include <minecraft:fog.glsl>
#include <minecraft:sample_lightmap.glsl>

layout(location = 0) in vec3 Position;
layout(location = 1) in vec4 Color;
layout(location = 2) in vec2 UV0;
layout(location = 3) in ivec2 UV1;
layout(location = 4) in ivec2 UV2;
layout(location = 5) in vec3 Normal;
uniform sampler2D Sampler2;

layout(location = 0) out float sphericalVertexDistance;
layout(location = 1) out float cylindricalVertexDistance;
layout(location = 2) out vec2 texCoord0;
layout(location = 3) out vec4 vertexColor;
layout(location = 4) out vec3 wallPosition;
layout(location = 5) out float wallHeight;
layout(location = 6) flat out vec2 cellMin;
layout(location = 7) flat out vec3 viewOrigin;
layout(location = 8) flat out int ownsEdge;
layout(location = 9) flat out int isGlow;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    sphericalVertexDistance = fog_spherical_distance(Position);
    cylindricalVertexDistance = fog_cylindrical_distance(Position);
    texCoord0 = UV0;
    ivec2 light = ivec2(UV2.y & 15, (UV2.y >> 4) & 15) * 16;
    vertexColor = Color * sample_lightmap(Sampler2, light);
    wallPosition = Position;
    wallHeight = float(UV2.x) / 4096.0;
    cellMin = vec2(UV1) + CameraOffset.xz;
    vec4 eye = inverse(ProjMat * ModelViewMat) * vec4(0.0, 0.0, 1.0, 0.0);
    viewOrigin = eye.xyz / eye.w;
    ownsEdge = Normal.x > 0.5 ? 1 : 0;
    isGlow = Normal.y < 0.5 ? 1 : 0;
}
