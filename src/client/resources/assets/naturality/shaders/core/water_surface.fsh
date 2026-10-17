#version 330
#extension GL_ARB_separate_shader_objects : require
#include <minecraft:projection.glsl>
#include <minecraft:dynamictransforms.glsl>
#include <naturality:water.glsl>
#include <naturality:water_ripples.glsl>
uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
uniform sampler2D NaturalityWaterMask;
uniform sampler2D NaturalityWaterSunAtlas;
layout(std140) uniform NaturalityWaterSun {
    mat4 WaterSunInversePose;
    vec4 WaterSunBounds;
    vec4 WaterSunAppearance; // rain/cloud transmission, reserved
};
layout(location = 0) in vec2 texCoord;
layout(location = 1) flat in mat4 waterInverseProjection;
layout(location = 0) out vec4 fragColor;

vec3 surface_offset(vec2 uv, float depth) {
    #ifndef RENDERPEARL_DEPTH_IS_ZERO_TO_ONE
    depth = depth * 2.0 - 1.0;
    #endif
    vec4 p = waterInverseProjection * vec4(uv * 2.0 - 1.0, depth, 1.0);
    return transpose(mat3(ModelViewMat)) * (p.xyz / p.w);
}

void main() {
    vec4 original = texture(Sampler0, texCoord);
    fragColor = original;
    vec4 surface = texture(NaturalityWaterMask, texCoord);
    float foreground = texture(Sampler1, texCoord).r;
    if (surface.r <= 0.0 || surface.a <= 0.0 || foreground > surface.r + 0.000001) return;
    vec3 offset = surface_offset(texCoord, surface.r);
    vec3 position = WaterCamera.xyz + offset;
    vec4 column = naturality_water_geometry(position.xz);
    if (column.w == 0.0 || abs(position.y - column.x) > 0.15 || offset.y <= 0.0) return;
    vec2 slope = naturality_water_ripple_slope(position, ivec3(WaterMap.x, 0, WaterMap.y));
    vec3 viewSlope = mat3(ModelViewMat) * vec3(slope.x, 0.0, slope.y);
    vec2 size = vec2(textureSize(Sampler0, 0));
    // Small angular deflection, quantized to screen pixels. No transparency holes.
    vec2 delta = viewSlope.xy * vec2(ProjMat[0][0], ProjMat[1][1]) * 0.035;
    if (WaterSwitches.y == 0) delta = vec2(0.0);
    vec2 uv = texCoord + round(delta * size) / size;
    if (any(lessThan(uv, 0.5 / size)) || any(greaterThan(uv, 1.0 - 0.5 / size))) return;
    vec4 other = texture(NaturalityWaterMask, uv);
    float otherDepth = texture(Sampler1, uv).r;
    // Reject foreground samples and unrelated water surfaces at silhouettes.
    if (other.r <= 0.0 || other.a <= 0.0 || otherDepth > other.r + 0.000001) return;
    vec3 otherOffset = surface_offset(uv, other.r);
    float separation = abs(length(otherOffset) - length(offset));
    float tolerance = max(0.5, length(offset) * 0.03);
    float weight = (1.0 - smoothstep(tolerance, tolerance * 2.0, separation))
        * smoothstep(0.0, 0.25, normalize(offset).y) * min(surface.a, other.a);
    fragColor = mix(original, texture(Sampler0, uv), weight);
    // Reconstruct only the actual sun sprite, never bright clouds or scenery.
    // Opaque geometry beyond the water also blocks the source; clouds additionally
    // use the renderer's cloud-coverage query, including transparent cloud layers.
    bool clearSource = otherDepth <= 0.0 || abs(otherDepth - other.r) <= 0.000001;
    if (WaterSunAppearance.x > 0.0 && clearSource) {
        vec3 localRay = mat3(WaterSunInversePose) * normalize(otherOffset);
        if (localRay.y > 0.0) {
            vec2 sunUV = localRay.xz / localRay.y * (100.0 / 60.0) + 0.5;
            // Small rippled edges retain a compact, mostly contiguous bright core.
            sunUV += slope * 0.025;
            if (all(greaterThanEqual(sunUV, vec2(0))) && all(lessThanEqual(sunUV, vec2(1)))) {
                vec4 source = texture(NaturalityWaterSunAtlas, mix(WaterSunBounds.xy, WaterSunBounds.zw, sunUV));
                float luminance = dot(source.rgb, vec3(0.2126, 0.7152, 0.0722));
                float core = smoothstep(0.65, 0.92, luminance) * source.a;
                float visibility = WaterSunAppearance.x * min(surface.a, other.a);
                vec2 center = sunUV - 0.5;
                float halo = exp(-dot(center, center) / 0.045) * 0.12 * visibility;
                fragColor.rgb += WaterTint.rgb * halo;
                // Full-bright source texels bypass water tint and surface lighting.
                fragColor.rgb = mix(fragColor.rgb, max(fragColor.rgb, source.rgb), core * visibility);
            }
        }
    }
}
