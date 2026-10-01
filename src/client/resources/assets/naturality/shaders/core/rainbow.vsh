#version 330
#extension GL_ARB_separate_shader_objects : require
#include <minecraft:projection.glsl>
#include <minecraft:dynamictransforms.glsl>
layout(location = 0) in vec3 Position;
layout(location = 0) out vec2 ringPlane;
layout(location = 1) out float skyHeight;
void main() {
    float sunAngle = ColorModulator.y;
    float radius = radians(mix(28.0, 46.0, ColorModulator.w));
    // Keep the center and height behavior while drawing the whole ring on one flat plane.
    // Keep the full-size ring fixed in the sky. Smaller rings lift their center.
    // The sun angle only chooses the east/west side; its height never moves the arc.
    float centerElevation = radians(-20.0) + 1.4 * (radians(46.0) - radius);
    float side = sin(sunAngle) < 0.0 ? -1.0 : 1.0;
    vec3 oppositeSun = vec3(side * cos(centerElevation), sin(centerElevation), 0.0);
    vec3 up = vec3(-side * sin(centerElevation), cos(centerElevation), 0.0);
    vec3 direction = 100.0 * oppositeSun + Position.x * vec3(0.0, 0.0, 1.0)
        + Position.y * up;
    skyHeight = direction.y / length(direction);
    ringPlane = Position.xy;
    gl_Position = ProjMat * ModelViewMat * vec4(direction, 1.0);
}
