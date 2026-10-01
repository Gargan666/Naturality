// Shared by the portal, frame glow and motes. Pulse 1 doubles both saturation
// (distance from luminance grey) and brightness; pulse 0 preserves the source.
vec3 naturality_portal_pulse(vec3 source, float pulse) {
    pulse = clamp(pulse, 0.0, 1.0);
    float luminance = dot(source, vec3(0.2126, 0.7152, 0.0722));
    vec3 saturated = mix(vec3(luminance), source, 1.0 + pulse);
    return max(saturated, vec3(0.0)) * (1.0 + pulse);
}
