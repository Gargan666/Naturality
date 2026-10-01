#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:fog.glsl>
#include <minecraft:projection.glsl>
#include <minecraft:dynamictransforms.glsl>
#include <naturality:water.glsl>
uniform sampler2D NaturalityWaterMask;

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
layout(location = 0) in vec2 texCoord;
layout(location = 0) out vec4 fragColor;

vec3 world_offset(vec2 uv, float depth) {
    #ifndef RENDERPEARL_DEPTH_IS_ZERO_TO_ONE
    depth = depth * 2.0 - 1.0;
    #endif
    vec4 view = inverse(ProjMat) * vec4(uv * 2.0 - 1.0, depth, 1.0);
    return transpose(mat3(ModelViewMat)) * (view.xyz / view.w);
}

void main() {
    float depth = texture(Sampler1, texCoord).r;
    vec4 water = texture(NaturalityWaterMask, texCoord);
    bool waterSurface = WaterMap.w != 0 && WaterSwitches.x != 0 && water.a > 0.5;
    // Overworld terrain once again receives distance fog in its material
    // shader. Only water needs a separate surface-distance atmosphere blend.
    if (FogColor.a >= 0.0 && !waterSurface) { fragColor = vec4(0); return; }
    // Reversed depth: zero is the unobstructed sky, which needs no overlay.
    vec3 position = world_offset(texCoord, max(depth, 0.000001));
    float distance = waterSurface ? water.g : fog_cylindrical_distance(position);
    ivec2 size = textureSize(Sampler1, 0);
    ivec2 center = clamp(ivec2((floor(gl_FragCoord.xy / NATURALITY_FOG_PIXEL_SIZE) + 0.5)
        * NATURALITY_FOG_PIXEL_SIZE), ivec2(0), size - 1);
    float centerDepth = texelFetch(Sampler1, center, 0).r;
    // Foliage and rough terrain often reject the center depth because it
    // belongs to another face. Still evaluate this face at the pixel-cell
    // center; falling back to per-fragment distance erased the pixel grid.
    float cellDistance = naturality_fog_cell_distance(distance);
    float edgeTolerance = max(2.0, distance * 0.05);
    float pixelDistance = clamp(cellDistance, distance - edgeTolerance, distance + edgeTolerance);
    if ((!waterSurface && depth <= 0.0) || !NATURALITY_FOG_ENABLED) { fragColor = vec4(0); return; }
    vec4 centerWater = texelFetch(NaturalityWaterMask, center, 0);
    if (waterSurface && centerWater.a > 0.5) {
        if (abs(centerWater.g - distance) <= max(2.0, distance * 0.05)) pixelDistance = centerWater.g;
    } else if (centerDepth > 0.0 && !waterSurface) {
        float centerDistance = fog_cylindrical_distance(world_offset((vec2(center) + 0.5) / vec2(size), centerDepth));
        // Do not transfer a background cell's opacity onto foreground silhouettes.
        if (abs(centerDistance - distance) <= max(2.0, distance * 0.05)) pixelDistance = centerDistance;
    }
    // Only render-distance fog. Overworld input contains atmosphere/sunset but
    // no celestial bodies, so even fully faded terrain still hides them.
    float amount = clamp((pixelDistance - FogRenderDistanceStart)
        / max(FogRenderDistanceEnd - FogRenderDistanceStart, 0.0001), 0.0, 1.0);
    if (distance <= FogRenderDistanceStart) amount = 0.0;
    // Each square has a continuous opacity, with a small fixed variation. The
    // variation vanishes at both ends, avoiding binary holes and popping.
    amount = smoothstep(0.0, 1.0, amount);
    amount = mix(amount, naturality_fog_density(amount),
        smoothstep(NATURALITY_FOG_BORDER_START, NATURALITY_FOG_BORDER_FULL, amount));
    // Render-distance fading must meet the actual atmosphere, even when the
    // camera is in local shade. Cave darkening belongs to environmental fog.
    vec3 worldFog = texture(Sampler0, texCoord).rgb;
    fragColor = vec4(worldFog, clamp(amount, 0.0, 1.0));
}
