#version 330
#extension GL_ARB_separate_shader_objects : require
#include <minecraft:dynamictransforms.glsl>
#include <minecraft:projection.glsl>

layout(location = 0) in vec3 Position;
layout(location = 1) in vec2 UV0;
layout(location = 0) out vec2 spriteUV;
layout(location = 1) flat out int element;
layout(location = 2) flat out float edgeFade;

void main() {
    const float ALONG[6] = float[6](0.26, 0.58, 0.94, 1.22, 1.59, 1.95);
    // The terminal ghost is three times the next-largest in both dimensions.
    const float SIZE[6] = float[6](0.026, 0.052, 0.019, 0.035, 0.072, 3.0 * 0.072);
    element = int(Position.z + 0.5);
    spriteUV = UV0;
    vec4 projected = ProjMat * vec4(ModelViewMat[3].xyz, 1.0);
    vec2 sun = projected.xy / max(projected.w, 0.0001);
    float closeness = ModelViewMat[0][0];
    // Unsnapped sprite centers slide through the center onto the opposite side.
    vec2 center = sun * (1.0 - ALONG[element] * mix(0.65, 1.25, closeness));
    // Projection aspect correction keeps the sprite AND its local texels square.
    float inverseAspect = abs(ProjMat[0][0] / ProjMat[1][1]);
    vec2 offset = Position.xy * SIZE[element] * vec2(inverseAspect, 1.0);
    // Keep the effect across the entire viewport, including wide-FOV corners.
    edgeFade = 1.0 - smoothstep(1.0, 1.10, max(abs(sun.x), abs(sun.y)));
    gl_Position = vec4(center + offset, 0.0, 1.0);
}
