#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:fog.glsl>
#include <minecraft:matrix.glsl>
#include <minecraft:globals.glsl>

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;

layout(location = 0) in vec4 texProj0;
layout(location = 1) in float sphericalVertexDistance;
layout(location = 2) in float cylindricalVertexDistance;
layout(location = 3) in vec3 portalPosition;
layout(location = 4) flat in vec3 portalViewOrigin;

#include <naturality:end_portal_colors.glsl>
#include <naturality:feature_settings.glsl>

const mat4 SCALE_TRANSLATE = mat4(
    0.5, 0.0, 0.0, 0.25,
    0.0, 0.5, 0.0, 0.25,
    0.0, 0.0, 1.0, 0.0,
    0.0, 0.0, 0.0, 1.0
);

mat4 end_portal_layer(float layer) {
    mat4 translate = mat4(
        1.0, 0.0, 0.0, 17.0 / layer,
        0.0, 1.0, 0.0, (2.0 + layer / 1.5) * (GameTime * 1.5),
        0.0, 0.0, 1.0, 0.0,
        0.0, 0.0, 0.0, 1.0
    );

    mat2 rotate = mat2_rotate_z(radians((layer * layer * 4321.0 + layer * 9.0) * 2.0));

    mat2 scale = mat2((4.5 - layer / 4.0) * 2.0);

    return mat4(scale * rotate) * translate * SCALE_TRANSLATE;
}

layout(location = 0) out vec4 fragColor;

// Intersect the camera ray with horizontal planes behind the visible surface.
// Keep the camera-relative ray separate from the world texture anchor: turning
// the camera must not drag the stars, and adjacent portal blocks must join.
vec2 portal_plane_uv(float depth, float layer) {
    vec3 ray = portalPosition - portalViewOrigin;
    float verticalDistance = max(abs(ray.y), 0.001);
    vec2 hit = portalPosition.xz + ray.xz * (depth / verticalDistance);
    vec2 camera = vec2(CameraBlockPos.xz) - CameraOffset.xz;
    mat2 rotation = mat2_rotate_z(radians((layer * layer * 4321.0 + layer * 9.0) * 2.0));
    float scale = 0.035 + layer * 0.0025;
    // Reduce the camera phase before adding the local hit to retain local detail.
    vec2 phase = fract((camera * rotation) * scale);
    return (hit * rotation) * scale + phase
        + vec2(17.0 / (layer + 1.0), (2.0 + layer / 1.5) * GameTime * 1.5);
}

// A direction-only sky has no finite distance and therefore no translation
// parallax or apparent scale change when approaching the opening.
vec2 portal_backdrop_uv() {
    vec3 direction = normalize(portalPosition - portalViewOrigin);
    return vec2(atan(direction.z, direction.x) / (2.0 * 3.14159265) + 0.5,
        asin(clamp(direction.y, -1.0, 1.0)) / 3.14159265 + 0.5);
}

void main() {
    vec3 color;
#if PORTAL_LAYERS == 15
    if (NATURALITY_END_PARALLAX) {
    color = texture(Sampler0, portal_backdrop_uv()).rgb * COLORS[0];
    // Composite far to near, leaving some of the deeper stars visible through
    // foreground stars. Vanilla's black background remains an empty gap.
    for (int i = PORTAL_LAYERS - 1; i >= 0; i--) {
        float fraction = float(i) / float(PORTAL_LAYERS - 1);
        // Increasing gaps push the final layers far into the opening while
        // retaining a few readable foreground stars.
        float depth = mix(0.35, 48.0, pow(fraction, 1.65));
        vec2 uv = portal_plane_uv(depth, float(i + 1));
        ivec2 size = textureSize(Sampler1, 0);
        vec4 star = texelFetch(Sampler1, ivec2(floor(fract(uv) * vec2(size))), 0);
        if (star.a > 0.0 && max(star.r, max(star.g, star.b)) > 0.0) {
            // COLORS contains additive pass weights; remove that attenuation
            // so brightness and opacity can be controlled independently.
            vec3 tint = COLORS[i] / max(COLORS[i].r, max(COLORS[i].g, COLORS[i].b));
            float depthBrightness = pow(0.18, fraction);
            float opacity = 0.65 * star.a;
            color = mix(color, star.rgb * tint * depthBrightness, opacity);
        }
    }
    } else
#endif
    {
    // End gateways share this shader; retain their original all-face projection.
    color = textureProj(Sampler0, texProj0).rgb * COLORS[0];
    for (int i = 0; i < PORTAL_LAYERS; i++) {
        color += textureProj(Sampler1, texProj0 * end_portal_layer(float(i + 1))).rgb * COLORS[i];
    }
    }
    fragColor = apply_fog(vec4(color, 1.0), sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
}
