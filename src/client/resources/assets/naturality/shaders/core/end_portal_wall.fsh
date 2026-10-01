#version 330
#extension GL_ARB_separate_shader_objects : require
#include <minecraft:fog.glsl>
#include <minecraft:projection.glsl>
#include <minecraft:dynamictransforms.glsl>
#include <minecraft:globals.glsl>
#include <naturality:end_portal_window.glsl>
#include <naturality:end_portal_glow.glsl>
// All OIT passes must use the same depth as the portal-window overlay.
vec4 wallFragCoord;
#define gl_FragCoord wallFragCoord
#include <minecraft:oit.glsl>
#undef gl_FragCoord
uniform sampler2D Sampler0;
layout(location = 0) in float sphericalVertexDistance;
layout(location = 1) in float cylindricalVertexDistance;
layout(location = 2) in vec2 texCoord0;
layout(location = 3) in vec4 vertexColor;
layout(location = 4) in vec3 wallPosition;
layout(location = 5) in float wallHeight;
layout(location = 6) flat in vec2 cellMin;
layout(location = 7) flat in vec3 viewOrigin;
layout(location = 8) flat in int ownsEdge;
layout(location = 9) flat in int isGlow;
#ifndef OIT_ALPHA_ONLY
layout(location = 0) out vec4 fragColor;
#endif
void main() {
    // Actual upper side faces remain terrain. Only reveal the submerged part.
    if (wallHeight <= 0.0 || (isGlow == 0 && wallHeight >= 0.75)) discard;
    // Depth belongs to the portal window, not to the virtual wall behind it.
    // Foreground blocks still occlude the overlay; the portal itself does not.
    float depth = gl_FragCoord.z;
    if (wallHeight < 0.75) {
        float separation = isGlow == 1 ? 1.0 / 512.0 : 1.0 / 1024.0;
        if (!end_portal_window(wallPosition, wallHeight, viewOrigin, cellMin, separation, depth)) discard;
    } else if (ownsEdge == 0) discard;
    gl_FragDepth = depth;
    wallFragCoord = vec4(gl_FragCoord.xy, gl_FragDepth, gl_FragCoord.w);
    vec4 color;
    if (isGlow == 1) {
        color = end_portal_glow_color(texCoord0, wallHeight, vertexColor.a) * ColorModulator;
    } else {
        color = texture(Sampler0, texCoord0) * vertexColor * ColorModulator;
        color.a *= end_portal_side_alpha(wallHeight);
    }
    if (color.a <= 0.0) discard;
#ifdef OIT_ALPHA_ONLY
    executeAlphaOnlyPhase(gl_FragDepth, color.a);
#else
    vec4 fogColor = FogColor;
#ifdef OIT_ACCUMULATE
    color = sampleColorForAccumulation(color);
    fogColor.rgb *= color.a;
#endif
    fragColor = apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance,
        FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, fogColor);
#endif
}
