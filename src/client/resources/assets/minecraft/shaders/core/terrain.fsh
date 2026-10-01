#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:fog.glsl>
#ifdef NATURALITY_SODIUM
#include <sodium:globals.glsl>
#define FogColor u_FogColor
#define FogEnvironmentalStart u_EnvironmentFog.x
#define FogEnvironmentalEnd u_EnvironmentFog.y
#define FogRenderDistanceStart u_RenderFog.x
#define FogRenderDistanceEnd u_RenderFog.y
#define UseRgss int(u_UseRGSS)
#define TextureSize (1.0 / u_TexelSize)
#define Sampler0 u_BlockTex
#define Sampler2 u_LightTex
#else
#include <minecraft:globals.glsl>
#include <minecraft:terrainglobals.glsl>
#endif
#include <minecraft:texture_sampling.glsl>
#include <minecraft:oit.glsl>
#include <naturality:pixel_lighting.glsl>
#include <naturality:procedural_fluids.glsl>
#include <naturality:feature_settings.glsl>
#ifndef NATURALITY_SODIUM
#include <naturality:procedural_fire.glsl>
#endif
#include <naturality:water.glsl>
#if !defined(MULTIDRAW_TERRAIN) && !defined(NATURALITY_SODIUM)
    #include <minecraft:chunksection.glsl>
#endif

uniform sampler2D Sampler0;
uniform sampler2D NaturalityWaterOpaqueDepth;
uniform sampler2D NaturalityWaterAtmosphere;
#ifndef OIT_ALPHA_ONLY
uniform sampler2D Sampler2;
#endif

layout(location = 0) in float sphericalVertexDistance;
layout(location = 1) in float cylindricalVertexDistance;
layout(location = 2) in vec4 vertexColor;
layout(location = 3) in vec2 texCoord0;
layout(location = 4) in float chunkVisibility;
layout(location = 5) in vec3 naturalityBlockPosition;
layout(location = 6) in vec4 naturalityShade;
layout(location = 7) flat in ivec3 naturalitySectionOrigin;
layout(location = 8) flat in int naturalityFluidKind;
layout(location = 9) in vec2 naturalityFluidUV;
layout(location = 10) in vec3 naturalityFlow;
layout(location = 11) in vec2 naturalityLightLevels;
layout(location = 12) flat in int naturalityFireKind;
layout(location = 13) in vec2 naturalityFireUV;
layout(location = 14) flat in vec3 naturalityFireSeed;
layout(location = 15) flat in int naturalitySubmerged;

#ifndef OIT_ALPHA_ONLY
layout(location = 0) out vec4 fragColor;
#endif

vec4 calculateFinalColor(vec4 color, bool waterSprite, bool submergedTerrain) {
    #ifdef OIT_ACCUMULATE
    color = sampleColorForAccumulation(color);
    vec4 fogColor = vec4(FogColor.rgb * color.a, FogColor.a);
    #else
    vec4 fogColor = FogColor;
    #endif
    if (waterSprite && WaterMap.w != 0 && WaterSwitches.x != 0 && WaterEnvironment.x != 0 && NATURALITY_FOG_ENABLED)
        return color;
    // Atmospheric environmental fog and distance fog are separate vanilla
    // inputs. Neither should whiten scenery behind the water surface.
    if (submergedTerrain && WaterEnvironment.x != 0) return color;
    // Water absorption owns submerged scenery. Do not brighten the seabed
    // toward the atmosphere at the render-distance boundary. Keep environmental
    // fog (blindness, etc.) and the dry terrain/surface paths independent.
    float distanceStart = submergedTerrain ? 1.0e6 : FogRenderDistanceStart;
    float distanceEnd = submergedTerrain ? 1.0e6 : FogRenderDistanceEnd;
    vec4 result = apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd, distanceStart, distanceEnd, fogColor);
    if (!submergedTerrain && WaterEnvironment.x != 0 && NATURALITY_FOG_ENABLED) {
        // Replace only distance fog's flat RGB with the sky behind this fragment.
        // Keep environmental/cave coverage and premultiplied OIT alpha intact.
        vec2 fogUV = gl_FragCoord.xy / vec2(textureSize(NaturalityWaterAtmosphere, 0));
        vec3 atmosphere = texture(NaturalityWaterAtmosphere, fogUV).rgb;
        #ifdef OIT_ACCUMULATE
        atmosphere *= color.a;
        #endif
        float distanceFog = linear_fog_value(cylindricalVertexDistance, distanceStart, distanceEnd);
        result.rgb += (atmosphere - fogColor.rgb) * distanceFog;
    }
    // Underwater surface extinction belongs to the depth-tested water draw,
    // not a fullscreen pass using opaque depth behind translucent entities.
    if (waterSprite && WaterMap.w != 0 && WaterSwitches.x != 0)
        result.rgb *= 1.0 - naturality_water_distance_fade(sphericalVertexDistance);
    return result;
}

void main() {
    bool waterSprite = false;
    for (int i = 0; i < 2; i++) {
        vec4 bounds = NaturalityFluidBounds[i];
        waterSprite = waterSprite || (bounds.x >= 0.0 && all(greaterThanEqual(texCoord0, bounds.xy)) && all(lessThanEqual(texCoord0, bounds.zw)));
    }
    #ifdef NATURALITY_WATER_MASK
    if (!waterSprite) discard;
    fragColor = vec4(gl_FragCoord.z, cylindricalVertexDistance, sphericalVertexDistance, 1.0);
    return;
    #endif
    vec4 shade = vec4(naturality_pixel_lighting(naturalityBlockPosition, naturalityShade.rgb), naturalityShade.a);
    vec4 light = vec4(naturality_pixel_lighting(naturalityBlockPosition, vertexColor.rgb), vertexColor.a);
    vec3 waterPosition = naturalityBlockPosition + vec3(naturalitySectionOrigin - ivec3(WaterMap.x, 0, WaterMap.y));
    // Offset perpendicular to the visible face, rather than diagonally toward
    // the camera (which selected different columns along cliffs when moving).
    vec3 faceNormal = normalize(cross(dFdx(naturalityBlockPosition), dFdy(naturalityBlockPosition)));
    faceNormal *= dot(faceNormal, WaterCamera.xyz - waterPosition) < 0.0 ? -1.0 : 1.0;
    vec4 waterColumn = naturality_water_column((waterPosition + faceNormal * 0.02).xz);
    float waterDepth = naturality_water_depth(waterPosition, waterColumn);
    float daylight = naturality_water_daylight(waterDepth, waterColumn.z);
    // Hide the finite column-map boundary in a broad transition, not a square.
    float mapEdge = min(min(waterPosition.x, waterPosition.z), min(256.0 - waterPosition.x, 256.0 - waterPosition.z));
    float mapWeight = smoothstep(0.0, 24.0, mapEdge);
    daylight = mix(1.0, daylight, mapWeight);
    #ifndef OIT_ALPHA_ONLY
    // Remove only skylight/ambient. The block-light-only lightmap sample retains
    // torches, sea lanterns and the existing fractional dynamic lighting field.
    vec2 lightUV = clamp(naturalityLightLevels, vec2(0.5 / 16.0), vec2(15.5 / 16.0));
    vec3 localLight = texture(Sampler2, vec2(lightUV.x, 0.5 / 16.0)).rgb;
    float blockLevel = clamp((lightUV.x * 16.0 - 0.5) / 15.0, 0.0, 1.0);
    localLight *= smoothstep(0.0, 0.06, blockLevel);
    localLight = naturality_pixel_lighting(naturalityBlockPosition, localLight);
    // Retain actual propagated skylight. Adding column depth to the lightmap
    // coordinate invented daylight under overhangs and along submerged cliffs.
    light.rgb = mix(min(light.rgb, localLight), light.rgb, daylight);
    #endif
    // Molten surfaces are self-lit. Skip lightmap, ambient occlusion and
    // directional face shading so lava sides keep the same brightness as tops.
    vec4 lighting = naturalityFluidKind >= 2 && NATURALITY_EMISSIVE_LAVA ? vec4(1.0) : vec4(
        naturality_adaptive_light(light.rgb)
        * naturality_step_brightness(shade.rgb, NATURALITY_AO_STEPS), light.a * shade.a);
    vec4 color = (UseRgss == 1 ? sampleRGSS(Sampler0, texCoord0, 1.0f / TextureSize) : sampleNearest(Sampler0, texCoord0, 1.0f / TextureSize)) * lighting;
    if (naturalityFluidKind >= 0)
        color = naturality_fluid_color(naturalityFluidKind, naturalityBlockPosition, naturalitySectionOrigin, naturalityFluidUV, naturalityFlow) * lighting;
    #ifndef NATURALITY_SODIUM
    if (naturalityFireKind >= 0)
        color = naturality_fire_color(naturalityFireKind, naturalityFireUV,
            naturality_fire_resolution(naturalityBlockPosition, naturalityFireUV), naturalityFireSeed);
    #endif
    if (waterSprite && WaterMap.w != 0 && WaterSwitches.x != 0)
        color.a = 0.60;
    if (waterSprite && WaterMap.w != 0 && WaterSwitches.x != 0) {
        // The overhead surface remains visible nearby, but recedes into black
        // over a substantially longer range than submerged scenery.
        float surfaceFade = naturality_water_distance_fade(sphericalVertexDistance);
        color.a = mix(color.a, 1.0, surfaceFade);
        if (WaterTime.y < 0.5) {
            float opaqueDepth = texelFetch(NaturalityWaterOpaqueDepth, ivec2(gl_FragCoord.xy), 0).r;
            // No opaque bed: the water's lit biome color is the approximation.
            // This replaces sky/sun transmission rather than tinting those pixels.
            float coverage = opaqueDepth <= 0.0 ? 1.0 : 0.0;
            // Missing terrain represents dark deep water, not an extra-bright
            // opaque copy of the surface. Match 60% water over a dark bed so
            // loaded-chunk contours do not become bright tinted patches.
            color.rgb *= mix(1.0, color.a, coverage);
            color.a = mix(color.a, 1.0, coverage);
        }
    }
    if (naturalityFireKind < 0) color.rgb = naturality_shadow_saturation(color.rgb, lighting.rgb);
    // Keep fog on the depth-tested water geometry rather than over the final
    // entity image. Coverage must match in the alpha and accumulation OIT phases.
    float waterFog = linear_fog_value(cylindricalVertexDistance, FogRenderDistanceStart, FogRenderDistanceEnd);
    if (waterSprite && WaterMap.w != 0 && WaterSwitches.x != 0 && WaterEnvironment.x != 0 && NATURALITY_FOG_ENABLED) {
        vec2 fogUV = gl_FragCoord.xy / vec2(textureSize(NaturalityWaterAtmosphere, 0));
        color.rgb = mix(color.rgb, texture(NaturalityWaterAtmosphere, fogUV).rgb, waterFog);
        color.a = mix(color.a, 1.0, waterFog);
    }
    vec3 surfaceNormal = abs(cross(dFdx(naturalityBlockPosition), dFdy(naturalityBlockPosition)));
    // Derivatives must be evaluated before the per-fragment floor eligibility
    // branch; divergent derivative sampling caused unstable shimmer at edges.
    float pattern = 0.0;
    if (WaterSwitches.z != 0)
        pattern = naturality_fluid_color(0, naturalityBlockPosition, naturalitySectionOrigin,
            naturalityBlockPosition.xz, vec3(0)).r;
    if (WaterSwitches.z != 0 && waterDepth > 2.0 && waterPosition.y >= waterColumn.y - 0.08
            && waterPosition.y <= waterColumn.y + 0.08 && surfaceNormal.y > max(surfaceNormal.x, surfaceNormal.z) * 4.0) {
        // Project the SAME still-water field and lattice vertically onto exposed
        // floor tops. Dark sprite areas contribute zero, never a dark decal.
        if ((NaturalityFluidInfo.w & 1) == 0) {
            vec4 bounds = NaturalityFluidBounds[0];
            vec2 pixel = (floor(fract(naturalityBlockPosition.xz) * 16.0) + 0.5) / 16.0;
            pattern = dot(texture(Sampler0, mix(bounds.xy, bounds.zw, pixel)).rgb, vec3(0.2126, 0.7152, 0.0722));
        }
        float highlight = smoothstep(0.47, 0.82, pattern);
        float sun = WaterCamera.w * clamp(naturalityLightLevels.y * 16.0 - 0.5, 0.0, 15.0) / 15.0;
        float luminance = dot(color.rgb, vec3(0.2126, 0.7152, 0.0722));
        vec3 saturated = max(vec3(0), mix(vec3(luminance), color.rgb, 1.65));
        float shimmerOpacity = clamp(highlight * sun * exp(-waterDepth * 0.065)
            * (1.0 - waterColumn.z) * 0.65 * smoothstep(2.0, 3.0, waterDepth) * mapWeight, 0.0, 0.8);
        color.rgb = mix(color.rgb, clamp(color.rgb + saturated, 0.0, 1.0), shimmerOpacity);
    }
    // Chunk arrival is independent of the distance-fog sentinel. Preserve
    // opacity in both OIT phases; newly arriving water must fade identically.
    if (waterSprite) color.a *= chunkVisibility;
    else if (naturalitySubmerged != 0 || (WaterMap.w != 0 && WaterTime.y > 0.5)) {
        // Opaque terrain cannot alpha-blend in place. Stable subpixel coverage
        // reveals the already-rendered background without a bright fog flash,
        // and discarded samples write neither color nor depth.
        float coverage = fract(52.9829189 * fract(dot(floor(gl_FragCoord.xy), vec2(0.06711056, 0.00583715))));
        if (chunkVisibility < 1.0 && coverage >= chunkVisibility) discard;
    } else {
        vec3 arrivalFog = FogColor.rgb;
        if (WaterEnvironment.x != 0 && NATURALITY_FOG_ENABLED)
            arrivalFog = texture(NaturalityWaterAtmosphere,
                gl_FragCoord.xy / vec2(textureSize(NaturalityWaterAtmosphere, 0))).rgb;
        color.rgb = mix(arrivalFog, color.rgb, chunkVisibility);
    }
    #ifdef ALPHA_CUTOUT
    if (color.a < ALPHA_CUTOUT) {
        discard;
    }
    #endif

    #ifdef OIT_ALPHA_ONLY
    executeAlphaOnlyPhase(gl_FragCoord.z, color.a);
    #else
    // A shoreline cube extends above its neighbor's fluid surface. Its exposed
    // strip must keep atmospheric fog even though the lower face is submerged.
    bool submergedTerrain = naturalitySubmerged != 0 && WaterSwitches.x != 0
        && (naturalitySubmerged == 9 || fract(naturalityBlockPosition.y) < float(naturalitySubmerged) / 9.0);
    #ifdef NATURALITY_SODIUM
    submergedTerrain = !waterSprite && waterDepth > 0.02 && WaterSwitches.x != 0;
    #endif
    fragColor = calculateFinalColor(color, waterSprite, submergedTerrain);
    #endif
}

