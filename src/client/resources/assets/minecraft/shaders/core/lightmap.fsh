#version 330
#extension GL_ARB_separate_shader_objects : require

layout(std140) uniform LightmapInfo {
    float SkyFactor;
    float BlockFactor;
    float NightVisionFactor;
    float DarknessScale;
    float BossOverlayWorldDarkeningFactor;
    float BrightnessFactor;
    vec3 BlockLightTint;
    vec3 SkyLightColor;
    vec3 AmbientColor;
    vec3 NightVisionColor;
} lightmapInfo;

layout(location = 0) in vec2 texCoord;

layout(location = 0) out vec4 fragColor;

float get_brightness(float level) {
    return level / (4.0 - 3.0 * level);
}

vec3 notGamma(vec3 color) {
    float maxComponent = max(max(color.x, color.y), color.z);
    if (maxComponent <= 0.0) return vec3(0.0); // Exact darkness must not divide by zero.
    float maxInverted = 1.0f - maxComponent;
    float maxScaled = 1.0f - maxInverted * maxInverted * maxInverted * maxInverted;
    return color * (maxScaled / maxComponent);
}

float parabolicMixFactor(float level) {
    return (2.0 * level - 1.0) * (2.0 * level - 1.0);
}

void main() {
    // Calculate block and sky brightness levels based on texture coordinates
    float block_level = floor(texCoord.x * 16) / 15;
    float sky_level = floor(texCoord.y * 16) / 15;
    // Negative SkyFactor carries immersion blend; the original daylight factor
    // is folded into SkyLightColor at upload without changing extracted state.
    float skyFactor = lightmapInfo.SkyFactor;
    float immersion = 0.0;
    if (skyFactor < 0.0) {
        immersion = clamp(-skyFactor - 1.0, 0.0, 1.0);
        skyFactor = 1.0;
    }

    float block_brightness = get_brightness(block_level) * lightmapInfo.BlockFactor;
    float sky_brightness = get_brightness(sky_level) * skyFactor;
    // Retain the propagated skylight gradient through cave entrances. Zero sky
    // remains zero; block lights and night vision retain their normal behavior.
    sky_brightness = mix(sky_brightness, sqrt(get_brightness(sky_level)), immersion);

    // Calculate ambient color with or without night vision
    vec3 nightVisionColor = lightmapInfo.NightVisionColor * lightmapInfo.NightVisionFactor;
    vec3 color = max(lightmapInfo.AmbientColor, nightVisionColor);

    // Add sky light
    color += lightmapInfo.SkyLightColor * sky_brightness;

    // Add block light
    vec3 BlockLightColor = mix(lightmapInfo.BlockLightTint, vec3(1.0), 0.9 * parabolicMixFactor(block_level));
    color += BlockLightColor * block_brightness;

    // Apply boss overlay darkening effect
    color = mix(color, color * vec3(0.7, 0.6, 0.6), lightmapInfo.BossOverlayWorldDarkeningFactor);

    // Apply darkness effect scale
    color = color - vec3(lightmapInfo.DarknessScale);

    // Apply brightness
    color = clamp(color, 0.0, 1.0);
    vec3 notGamma = notGamma(color);
    color = mix(color, notGamma, lightmapInfo.BrightnessFactor);

    // A small, uniform underwater reduction after gamma: retain normal light
    // colors and time-of-day response without reintroducing depth attenuation.
    color *= mix(1.0, 0.85, immersion);

    fragColor = vec4(color, 1.0);
}

