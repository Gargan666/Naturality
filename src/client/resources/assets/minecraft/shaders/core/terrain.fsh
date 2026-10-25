#version 330
#extension GL_ARB_separate_shader_objects : require
#include <naturality:menu_region.glsl>

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
#include <naturality:cutout_pixels.glsl>
#include <naturality:texture_lod.glsl>
#include <naturality:procedural_fluids.glsl>
#include <naturality:feature_settings.glsl>
#include <naturality:procedural_fire.glsl>
#include <naturality:water.glsl>
#include <naturality:water_ripples.glsl>
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

layout(location = 16) flat in int naturalityGlassKind;
layout(location = 17) in vec3 naturalityGlassRelative;
layout(location = 18) flat in vec3 naturalityGlassForward;
#include <naturality:glass_shine.glsl>

vec4 calculateFinalColor(vec4 color, bool waterSprite, bool submergedTerrain) {
    vec3 waterOffset = naturalityBlockPosition
        + vec3(naturalitySectionOrigin - ivec3(WaterMap.x, 0, WaterMap.y)) - WaterCamera.xyz;
    float immersion = naturality_water_view_immersion(waterOffset);
    #ifdef OIT_ACCUMULATE
    color = sampleColorForAccumulation(color);
    vec4 fogColor = vec4(FogColor.rgb * color.a, FogColor.a);
    #else
    vec4 fogColor = FogColor;
    #endif
    // The underside already has premultiplied water haze. Vanilla water fog
    // would add a moon-independent bright band before our distance extinction.
    if (waterSprite && WaterMap.w != 0 && WaterSwitches.x != 0 && immersion > 0.5)
        return color;
    if (waterSprite && WaterMap.w != 0 && WaterSwitches.x != 0 && WaterEnvironment.x != 0 && NATURALITY_FOG_ENABLED)
        return color;
    // Atmospheric environmental fog and distance fog are separate vanilla
    // inputs. Neither should whiten scenery behind the water surface.
    if (submergedTerrain && WaterEnvironment.x != 0) {
        if (immersion > 0.5) return color;
        // The water map can outlive the visible surface mesh. Even those
        // uncovered seabed pixels must disappear at the camera's draw limit.
        float distance = max(length(waterOffset.xz), abs(waterOffset.y));
        float fade = naturality_water_fog_coverage(smoothstep(
            FogRenderDistanceEnd * 0.80, FogRenderDistanceEnd, distance));
        vec2 uv = gl_FragCoord.xy / vec2(textureSize(NaturalityWaterAtmosphere, 0));
        vec3 atmosphere = textureLod(NaturalityWaterAtmosphere, uv, 0.0).rgb;
        #ifdef OIT_ACCUMULATE
        atmosphere *= color.a;
        #endif
        return vec4(mix(color.rgb, atmosphere, fade), color.a);
    }
    // Water absorption owns submerged scenery. Do not brighten the seabed
    // toward the atmosphere at the render-distance boundary. Keep environmental
    // fog (blindness, etc.) and the dry terrain/surface paths independent.
    float distanceStart = submergedTerrain ? 1.0e6 : FogRenderDistanceStart;
    float distanceEnd = submergedTerrain ? 1.0e6 : FogRenderDistanceEnd;
    vec4 result = apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd, distanceStart, distanceEnd, fogColor);
    if (!submergedTerrain && WaterEnvironment.x != 0 && NATURALITY_FOG_ENABLED) {
        // Replace only distance fog's flat RGB with the sky behind this fragment.
        // Keep environmental/cave coverage and premultiplied OIT alpha intact.
        float distanceFog = linear_fog_value(cylindricalVertexDistance, distanceStart, distanceEnd);
        float rainFog = intBitsToFloat(WaterEnvironment.z);
        float environmentalFog = linear_fog_value(sphericalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd);
        float rainCoverage = max(0.0, environmentalFog - distanceFog) * rainFog;
        if (distanceFog != 0.0 || rainCoverage != 0.0) {
            vec2 fogUV = gl_FragCoord.xy / vec2(textureSize(NaturalityWaterAtmosphere, 0));
            vec3 atmosphere = textureLod(NaturalityWaterAtmosphere, fogUV, 0.0).rgb;
            #ifdef OIT_ACCUMULATE
            atmosphere *= color.a;
            #endif
            result.rgb += (atmosphere - fogColor.rgb) * distanceFog;
            result.rgb += (atmosphere - fogColor.rgb * naturality_world_fog_brightness(fogColor.a)) * max(0.0, environmentalFog - distanceFog) * rainFog;
        }
    }
    // Underwater surface extinction belongs to the depth-tested water draw,
    // not a fullscreen pass using opaque depth behind translucent entities.
    if (waterSprite && WaterMap.w != 0 && WaterSwitches.x != 0) {
        vec3 waterFogColor = naturality_water_directional_fog_color(normalize(waterOffset).y);
        #ifdef OIT_ACCUMULATE
        waterFogColor *= color.a;
        #endif
        result.rgb = mix(result.rgb, waterFogColor, naturality_water_surface_fade(sphericalVertexDistance, waterOffset));
    }
    return result;
}

void main() {
    #if defined(NATURALITY_WATER_OPAQUE_TERRAIN) && !defined(ALPHA_CUTOUT) && !defined(OIT_ALPHA_ONLY) && !defined(OIT_ACCUMULATE) && !defined(NATURALITY_WATER_MASK)
    // Section-uniform selection keeps derivative-based shading in neighboring
    // visible primitives intact. Include fitted snow's 32-block downward reach
    // and wind/model displacement. Keep depth and raster coverage unchanged.
    if (WaterMap.w != 0 && WaterTime.y > 0.5 && WaterSwitches.x != 0
            && WaterParams.y > 0.0 && MenuBounds.w == 0.0 && chunkVisibility >= 1.0
            && naturalityFluidKind < 0 && naturalityFireKind < 0) {
        vec3 origin = vec3(naturalitySectionOrigin - ivec3(WaterMap.x, 0, WaterMap.y));
        vec3 nearest = clamp(WaterCamera.xyz, origin - vec3(2.0, 34.0, 2.0), origin + vec3(18.0));
        vec3 delta = nearest - WaterCamera.xyz;
        if (dot(delta, delta) > WaterParams.y * WaterParams.y) {
            // The following optics pass replaces this RGB completely with fog.
            // Opaque terrain still writes its original fragment depth.
            // Retain the original alpha contract even for resource packs with
            // alpha in solid-layer textures. Only the unused RGB work is skipped.
            float lod = naturality_texture_lod_weight(texCoord0, NaturalityLodBounds, sphericalVertexDistance);
            vec2 average = naturality_texture_average_uv(texCoord0, NaturalityLodBounds);
            vec4 sampled = lod >= 1.0 ? textureLod(Sampler0, average, 0.0)
                : naturality_texture_detail(Sampler0, texCoord0, 1.0 / TextureSize,
                    dFdx(texCoord0), dFdy(texCoord0), UseRgss == 1);
            float shadeAlpha = int(round(naturalityShade.a * 255.0)) == 242 ? 1.0 : naturalityShade.a;
            fragColor = vec4(0.0, 0.0, 0.0, sampled.a * (vertexColor.a * shadeAlpha));
            return;
        }
    }
    #endif
    // Atlas-edge interpolation must not discard individual water-mask pixels.
    bool waterSprite = naturalitySubmerged < 0;
    bool fallingFluidTail = naturalityFlow.z < -0.5;
    // Each sixteenth-block row has constant opacity; the lowest row is invisible.
    float tailOpacity = clamp(floor(fract(naturalityBlockPosition.y) * 16.0) / 15.0, 0.0, 1.0);
    if (fallingFluidTail && tailOpacity <= 0.0) discard;
    vec3 positionDx = dFdx(naturalityBlockPosition), positionDy = dFdy(naturalityBlockPosition);
    vec3 derivativeNormal = cross(positionDx, positionDy);
    vec3 menuPosition = naturalityBlockPosition + naturalitySectionOrigin;
    vec3 menuNormal = derivativeNormal / max(length(derivativeNormal), 0.000001);
    if (!naturality_menu_surface_visible(menuPosition, menuNormal)) discard;
    float menuEdge = naturality_menu_edge(menuPosition, menuNormal);
    if (MenuBounds.w > 0.0 && length(menuPosition.xz - MenuBounds.xy) >= MenuBounds.w) {
        // Fully fogged outer faces stay opaque and write depth, covering exposed
        // terrain/water cuts. Their color is exactly the sky behind this pixel.
        #ifdef NATURALITY_WATER_MASK
        discard;
        #elif defined(OIT_ALPHA_ONLY)
        executeAlphaOnlyPhase(gl_FragCoord.z, 1.0);
        #else
        vec4 cover = vec4(textureLod(NaturalityWaterAtmosphere,
            gl_FragCoord.xy / vec2(textureSize(NaturalityWaterAtmosphere, 0)), 0.0).rgb, 1.0);
        #ifdef OIT_ACCUMULATE
        cover = sampleColorForAccumulation(cover);
        #endif
        fragColor = cover;
        #endif
        return;
    }
    #ifdef NATURALITY_WATER_MASK
    if (!waterSprite) discard;
    // Reveal the complete water composition at the same samples as the actual
    // surface. Independently blending a black backdrop and translucent water
    // produces a dark intermediate rectangle rather than a scene crossfade.
    float arrivalSample = fract(52.9829189 * fract(dot(floor(gl_FragCoord.xy), vec2(0.06711056, 0.00583715))));
    if (chunkVisibility < 1.0 && arrivalSample >= chunkVisibility) discard;
    float waterCoverage = fallingFluidTail ? tailOpacity : 1.0;
    if (waterCoverage <= 0.0) discard;
    fragColor = vec4(gl_FragCoord.z, cylindricalVertexDistance, sphericalVertexDistance, waterCoverage);
    return;
    #endif
    bool texelLighting = int(round(naturalityShade.a * 255.0)) == 242;
    vec3 lightingGrid = texelLighting ? naturality_texel_lighting_grid(texCoord0, TextureSize)
        : naturality_pixel_grid(naturalityBlockPosition);
    // Sprite UVs own the tint grid, including rotated/mirrored foliage and packs
    // with a different resolution. Procedural fluids already use world texels.
    vec3 tintGrid = naturalityFluidKind >= 0 ? naturality_block_grid(naturalityBlockPosition)
        : naturality_texel_grid(texCoord0, TextureSize);
    vec4 shade = vec4(naturality_field_at_grid(tintGrid, naturalityShade.rgb),
        texelLighting ? 1.0 : naturalityShade.a);
    vec4 light = vec4(naturality_pixel_lighting_grid(lightingGrid, vertexColor.rgb), vertexColor.a);
    vec3 waterPosition = naturalityBlockPosition + vec3(naturalitySectionOrigin - ivec3(WaterMap.x, 0, WaterMap.y));
    float waterImmersion = naturality_water_view_immersion(waterPosition - WaterCamera.xyz);
    // Offset perpendicular to the visible face, rather than diagonally toward
    // the camera (which selected different columns along cliffs when moving).
    vec3 surfaceNormal = abs(cross(positionDx, positionDy));
    vec2 shimmerUV = vec2(0.0), shimmerUVDx = vec2(0.0), shimmerUVDy = vec2(0.0);
    if (WaterSwitches.z != 0 && (NaturalityFluidInfo.w & 1) == 0) {
        vec4 shimmerBounds = NaturalityFluidBounds[0];
        vec2 shimmerPixel = (floor(fract(naturalityBlockPosition.xz) * 16.0) + 0.5) / 16.0;
        shimmerUV = mix(shimmerBounds.xy, shimmerBounds.zw, shimmerPixel);
        shimmerUVDx = dFdx(shimmerUV); shimmerUVDy = dFdy(shimmerUV);
    }
    vec4 waterColumn = vec4(0.0);
    float waterDepth = 0.0, mapWeight = 1.0;
    if (WaterMap.w != 0) {
        vec3 faceNormal = normalize(cross(positionDx, positionDy));
        faceNormal *= dot(faceNormal, WaterCamera.xyz - waterPosition) < 0.0 ? -1.0 : 1.0;
        waterColumn = naturality_water_column((waterPosition + faceNormal * 0.02).xz);
        waterDepth = naturality_water_depth(waterPosition, waterColumn);
        // Hide the finite column-map boundary in a broad transition, not a square.
        float mapEdge = min(min(waterPosition.x, waterPosition.z), min(256.0 - waterPosition.x, 256.0 - waterPosition.z));
        mapWeight = smoothstep(0.0, 24.0, mapEdge);
    }
    // Both images live in the existing atlas; geometry, light and metadata stay
    // unchanged. The blend follows the camera every frame, including while paused.
    bool glassSprite = naturalityGlassKind >= 0;
    float textureFade = naturality_texture_lod_weight(texCoord0, NaturalityLodBounds, sphericalVertexDistance);
    vec2 averageUV = naturality_texture_average_uv(texCoord0, NaturalityLodBounds);
    #ifdef ALPHA_CUTOUT
    // Keep cutout alpha's ordinary mip coverage across the entire fade, even
    // when solid-leaf LOD is disabled. Only RGB transitions to the average.
    vec4 sampledColor = UseRgss == 1 ? sampleRGSS(Sampler0, texCoord0, 1.0f / TextureSize)
        : sampleNearest(Sampler0, texCoord0, 1.0f / TextureSize);
    if (naturalityFluidKind < 0 && naturalityFireKind < 0 && !waterSprite && !glassSprite)
        sampledColor = naturality_cutout_pixels(Sampler0, texCoord0, TextureSize, sampledColor);
    // All derivatives used by ordinary terrain have been captured above. Fluid
    // fire and glass shaders can replace alpha. Translucent terrain also defines
    // ALPHA_CUTOUT, so source-alpha-zero glass must reach its shine calculation
    // before the final alpha threshold is applied below.
    if (naturalityFluidKind < 0 && naturalityFireKind < 0 && !waterSprite && !glassSprite
            && sampledColor.a * (light.a * shade.a) < ALPHA_CUTOUT) discard;
    if (textureFade > 0.0)
        sampledColor.rgb = mix(sampledColor.rgb, textureLod(Sampler0, averageUV, 0.0).rgb, textureFade);
    #else
    vec2 textureDx = dFdx(texCoord0), textureDy = dFdy(texCoord0);
    vec4 sampledColor = textureFade >= 1.0 ? textureLod(Sampler0, averageUV, 0.0)
        : naturality_texture_detail(Sampler0, texCoord0, 1.0f / TextureSize, textureDx, textureDy, UseRgss == 1);
    if (textureFade > 0.0 && textureFade < 1.0)
        sampledColor.rgb = mix(sampledColor.rgb, textureLod(Sampler0, averageUV, 0.0).rgb, textureFade);
    #endif
    // Molten surfaces are self-lit. Skip lightmap, ambient occlusion and
    // directional face shading so lava sides keep the same brightness as tops.
    vec4 lighting = naturalityFluidKind >= 2 && NATURALITY_EMISSIVE_LAVA ? vec4(1.0) : vec4(
        naturality_adaptive_light(light.rgb)
        * naturality_step_brightness(shade.rgb, NATURALITY_AO_STEPS), light.a * shade.a);
    vec4 surfaceColor = sampledColor;
    if (naturalityFluidKind >= 0)
        surfaceColor = naturality_fluid_color(naturalityFluidKind, naturalityBlockPosition, naturalitySectionOrigin, naturalityFluidUV, naturalityFlow);
    vec4 color = surfaceColor * lighting;
    // Shade the original texture before adding the face-shading-independent reflection.
    if (glassSprite) color.rgb = naturality_shadow_saturation(color.rgb, lighting.rgb);
    color = naturality_glass_shine(color, light.rgb, positionDx, positionDy);
    if (naturalityFireKind >= 0)
        color = naturality_fire_color(naturalityFireKind, naturalityFireUV,
            naturality_fire_resolution(naturalityBlockPosition, naturalityFireUV), naturalityFireSeed);
    if (waterSprite && WaterMap.w != 0 && WaterSwitches.x != 0) {
        // Restore topside coverage: bright texture pixels are highlights,
        // not holes revealing the terrain below.
        color.a = 0.60;
    }
    if (waterSprite && WaterMap.w != 0 && WaterSwitches.x != 0 && waterImmersion > 0.5
            && waterPosition.y > WaterCamera.y
            && surfaceNormal.y > max(surfaceNormal.x, surfaceNormal.z) * 4.0) {
        // Use exactly the topside simulation, world-texel lattice and filtering.
        // Texture contrast belongs in RGB; transmission varies only with angle.
        float pattern = dot(surfaceColor.rgb, vec3(0.2126, 0.7152, 0.0722));
        // Selected softer/brighter underside: retain the fine texture without
        // pushing its troughs toward black or making its highlights transparent.
        float contrast = mix(0.82, 1.20, smoothstep(0.44, 0.78, pattern));
        color.rgb = surfaceColor.rgb * lighting.rgb * contrast;
        color.a = 0.48;
    }
    if (waterSprite && WaterMap.w != 0 && WaterSwitches.x != 0) {
        vec3 normal = normalize(derivativeNormal);
        if (surfaceNormal.y > max(surfaceNormal.x, surfaceNormal.z) * 4.0) {
            vec2 slope = naturality_water_ripple_slope(naturalityBlockPosition, naturalitySectionOrigin);
            normal = normalize(vec3(-slope.x, 1.0, -slope.y));
        }
        float facing = dot(normal, normalize(waterPosition - WaterCamera.xyz));
        float opacity = naturality_water_fresnel(facing);
        if (waterImmersion < 0.5) {
            // Bound the transparent window horizontally around the camera.
            // Altitude must not close the view into water directly below;
            // the outer ring still hides seabed beyond underwater visibility.
            float range = naturality_water_visibility_range();
            float distanceOpacity = naturality_water_fog_coverage(smoothstep(
                range * 0.65, range, length(waterPosition.xz - WaterCamera.xz)));
            opacity = max(opacity, distanceOpacity);
        }
        color.a = mix(color.a, 1.0, opacity);
        // Every water face must meet the same directional haze as the scenery.
        // A flat tint here leaves bright side/bottom faces in the dark lower view.
        float surfaceFade = naturality_water_surface_fade(sphericalVertexDistance, waterPosition - WaterCamera.xyz);
        vec3 surfaceFog = naturality_water_directional_fog_color(normalize(waterPosition - WaterCamera.xyz).y);
        vec3 foggedSurface = mix(color.rgb * color.a, surfaceFog, surfaceFade);
        color.a = mix(color.a, 1.0, surfaceFade);
        color.rgb = foggedSurface / max(color.a, 0.0001);
        // Coverage is evaluated on the water geometry in both sorted and OIT
        // passes, so foreground translucent objects retain their own ordering.
    }
    if (naturalityFireKind < 0 && !glassSprite) color.rgb = naturality_shadow_saturation(color.rgb, lighting.rgb);
    // Keep fog on the depth-tested water geometry rather than over the final
    // entity image. Coverage must match in the alpha and accumulation OIT phases.
    float waterFog = linear_fog_value(cylindricalVertexDistance, FogRenderDistanceStart, FogRenderDistanceEnd);
    if (waterSprite && WaterMap.w != 0 && WaterSwitches.x != 0 && waterImmersion < 0.5
            && WaterEnvironment.x != 0 && NATURALITY_FOG_ENABLED) {
        vec2 fogUV = gl_FragCoord.xy / vec2(textureSize(NaturalityWaterAtmosphere, 0));
        float fogOpacity = abs(FogColor.a) >= 2.0 ? 1.0 : FogColor.a;
        float coverage = total_fog_value(sphericalVertexDistance, cylindricalVertexDistance,
            FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd) * fogOpacity;
        // Fog covers the whole view through the surface, including transmitted
        // seabed color. Compose in premultiplied form before returning to the
        // straight-alpha color expected by both vanilla transparency paths.
        // Applying only distance fog here left holes in heavy-rain/cave haze.
        vec3 fogged = apply_fog(vec4(color.rgb * color.a, color.a),
            sphericalVertexDistance, cylindricalVertexDistance,
            FogEnvironmentalStart, FogEnvironmentalEnd,
            FogRenderDistanceStart, FogRenderDistanceEnd, FogColor).rgb;
        fogged += (textureLod(NaturalityWaterAtmosphere, fogUV, 0.0).rgb - FogColor.rgb) * waterFog * fogOpacity;
        float environmentalFog = linear_fog_value(sphericalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd);
        fogged += (textureLod(NaturalityWaterAtmosphere, fogUV, 0.0).rgb
            - FogColor.rgb * naturality_world_fog_brightness(FogColor.a))
            * max(0.0, environmentalFog - waterFog) * intBitsToFloat(WaterEnvironment.z) * fogOpacity;
        color.a = mix(color.a, 1.0, coverage);
        color.rgb = fogged / max(color.a, 0.0001);
    }
    // Derivatives must be evaluated before the per-fragment floor eligibility
    // branch; divergent derivative sampling caused unstable shimmer at edges.
    vec2 shimmerDx = positionDx.xz;
    vec2 shimmerDy = positionDy.xz;
    // Any exposed model top within the water column receives shimmer, including
    // stacked waterlogged stairs/trapdoors well above the seabed. Extend one block
    // below the wet interval for partial models and the exposed support below them.
    vec3 shimmerNormal = normalize(derivativeNormal);
    shimmerNormal *= dot(shimmerNormal, WaterCamera.xyz - waterPosition) < 0.0 ? -1.0 : 1.0;
    bool floorSurface = waterColumn.w > 0.0 && waterPosition.y >= waterColumn.y - 1.0
        && waterPosition.y < waterColumn.x - 0.02;
    if (WaterSwitches.z != 0 && !waterSprite && naturalityFluidKind < 2 && floorSurface
            && shimmerNormal.y > 0.9) {
        float pattern = naturality_still_water_pattern(naturalityBlockPosition, naturalitySectionOrigin, shimmerDx, shimmerDy);
        // Project the SAME still-water field and lattice vertically onto exposed
        // floor tops. Dark sprite areas contribute zero, never a dark decal.
        if ((NaturalityFluidInfo.w & 1) == 0) {
            pattern = dot(textureGrad(Sampler0, shimmerUV, shimmerUVDx, shimmerUVDy).rgb, vec3(0.2126, 0.7152, 0.0722));
        }
        float highlight = smoothstep(0.47, 0.82, pattern);
        // The column map already restricts this to exposed floor blocks. Sampling
        // attenuated seabed skylight again made deep-water shimmer disappear.
        float shimmerDepth = max(WaterTime.z, waterColumn.x - waterPosition.y);
        float sun = WaterCamera.w * (1.0 - smoothstep(8.0, 80.0, shimmerDepth));
        float luminance = dot(color.rgb, vec3(0.2126, 0.7152, 0.0722));
        vec3 saturated = max(vec3(0), mix(vec3(luminance), color.rgb, 1.65));
        vec3 shimmerTint = mix(vec3(1.0), WaterTint.rgb, 0.25);
        vec3 shimmerLight = max(saturated, vec3(0.18)) * shimmerTint;
        float shimmerOpacity = clamp(highlight * sun
            * (1.0 - waterColumn.z) * 0.65 * mapWeight, 0.0, 0.8);
        color.rgb = mix(color.rgb, clamp(color.rgb + shimmerLight, 0.0, 1.0), shimmerOpacity);
    }
    // The mask, surface (including both OIT phases) and submerged terrain must
    // reveal identical samples. Keep the material's intended alpha at each
    // revealed sample instead of fading the backdrop and surface separately.
    if (waterSprite || naturalitySubmerged != 0 || (WaterMap.w != 0 && WaterTime.y > 0.5)) {
        // Opaque terrain cannot alpha-blend in place. Stable subpixel coverage
        // reveals the already-rendered background without a bright fog flash,
        // and discarded samples write neither color nor depth.
        float coverage = fract(52.9829189 * fract(dot(floor(gl_FragCoord.xy), vec2(0.06711056, 0.00583715))));
        if (chunkVisibility < 1.0 && coverage >= chunkVisibility) discard;
    } else if (chunkVisibility != 1.0) {
        vec3 arrivalFog = FogColor.rgb;
        if (WaterEnvironment.x != 0 && NATURALITY_FOG_ENABLED)
            arrivalFog = textureLod(NaturalityWaterAtmosphere,
                gl_FragCoord.xy / vec2(textureSize(NaturalityWaterAtmosphere, 0)), 0.0).rgb;
        color.rgb = mix(arrivalFog, color.rgb, chunkVisibility);
    }
    if (fallingFluidTail) color.a *= tailOpacity;
    if (menuEdge > 0.0) {
        vec3 atmosphere = textureLod(NaturalityWaterAtmosphere,
                gl_FragCoord.xy / vec2(textureSize(NaturalityWaterAtmosphere, 0)), 0.0).rgb;
        color.rgb = mix(color.rgb, atmosphere, menuEdge);
    }
    #ifdef ALPHA_CUTOUT
    // OIT depth bounds discard alpha below 0.01. Use that same threshold for
    // glass in every pass, so emerging cells never accumulate without bounds.
    if (color.a < (glassSprite ? 0.01 : ALPHA_CUTOUT)) {
        discard;
    }
    #endif

    #ifdef OIT_ALPHA_ONLY
    executeAlphaOnlyPhase(gl_FragCoord.z, color.a);
    #else
    // A shoreline cube extends above its neighbor's fluid surface. Its exposed
    // strip must keep atmospheric fog even though the lower face is submerged.
    bool submergedTerrain = naturalitySubmerged > 0 && WaterSwitches.x != 0
        && (naturalitySubmerged == 9 || fract(naturalityBlockPosition.y) < float(naturalitySubmerged) / 9.0);
    #ifdef NATURALITY_SODIUM
    submergedTerrain = !waterSprite && waterDepth > 0.02 && WaterSwitches.x != 0;
    #endif
    fragColor = calculateFinalColor(color, waterSprite, submergedTerrain);
    #endif
}

