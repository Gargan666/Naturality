#ifndef NATURALITY_LAVA_FOG_GLSL
#define NATURALITY_LAVA_FOG_GLSL

// Shared by molten terrain and the lower-atmosphere composite.
vec3 naturality_lava_fog_color(float strength) {
    return mix(vec3(1.0, 0.24, 0.015), vec3(1.0, 0.68, 0.08), smoothstep(0.15, 0.9, strength));
}

float naturality_lava_atmosphere_visibility(float coverage) {
    // Keep terrain opaque through most of its color-fog span. Only the far
    // boundary releases the atmosphere behind it to conceal unloaded edges.
    return smoothstep(0.85, 1.0, coverage);
}

vec3 naturality_lava_surface_fog(vec3 surface, vec3 atmosphere, float coverage, float alphaScale) {
    float warmth = smoothstep(0.0, 0.7, coverage);
    vec3 molten = mix(surface, naturality_lava_fog_color(warmth) * alphaScale, warmth);
    // Opaque terrain cannot blend alpha in place. Fade its visible contribution
    // into the atmosphere here; the later glow composite releases its depth
    // occlusion over the same final interval, without discarding pixels or
    // introducing a dithered transparency pattern.
    float disappearance = naturality_lava_atmosphere_visibility(coverage);
    return mix(molten, atmosphere, disappearance);
}

#endif
