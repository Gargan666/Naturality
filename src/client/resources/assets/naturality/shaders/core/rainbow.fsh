#version 330
#extension GL_ARB_separate_shader_objects : require
#include <minecraft:dynamictransforms.glsl>
uniform sampler2D Sampler0;
layout(location = 0) in vec2 ringPlane;
layout(location = 1) in float skyHeight;
layout(location = 0) out vec4 fragColor;
bool corePixel(vec2 center, float outer, float inner) {
    float row = (outer - length(center)) * 12.0 / (outer - inner);
    return row >= 1.0 && row < 11.0;
}
void main() {
    float sunHeight = cos(ColorModulator.y);
    float daylight = smoothstep(0.0, 0.14, sunHeight);
    float horizon = smoothstep(0.0, 0.075, skyHeight);
    // Sample the whole curved ring at one square sky-space cell per sun pixel.
    // Opacity fades near the side switch; radius takes four times as much
    // celestial rotation to settle, and keeps changing during that fade.
    float middleDistance = abs(atan(sin(ColorModulator.y), cos(ColorModulator.y)));
    float sideFade = clamp(middleDistance / 0.45, 0.0, 1.0);
    float sizeProgress = smoothstep(0.0, 2.0, middleDistance);
    float middleExpansion = pow(1.0 - sizeProgress, 2.0);
    float radius = radians(mix(28.0, 46.0, ColorModulator.w) + 9.0 * middleExpansion);
    float pixelSize = 1.875;
    vec2 cell = floor(ringPlane / pixelSize);
    vec2 pixelCenter = (cell + 0.5) * pixelSize;
    float outer = 100.0 * tan(radius + 0.1125);
    float inner = 100.0 * tan(radius - 0.1125);
    float row = (outer - length(pixelCenter)) * 12.0 / (outer - inner);
    if (row < 0.0 || row >= 12.0) discard;
    float acrossPixel = floor(row);
    bool edgeRow = acrossPixel < 1.0 || acrossPixel >= 11.0;
    // Add fringe pixels only where the opaque arc turns a pixelated corner.
    if (edgeRow) {
        bool xNeighbor = corePixel(pixelCenter + vec2(pixelSize, 0.0), outer, inner)
            || corePixel(pixelCenter - vec2(pixelSize, 0.0), outer, inner);
        bool yNeighbor = corePixel(pixelCenter + vec2(0.0, pixelSize), outer, inner)
            || corePixel(pixelCenter - vec2(0.0, pixelSize), outer, inner);
        if (!xNeighbor || !yNeighbor) discard;
    }
    float edge = edgeRow ? 0.32 : 1.0;
    float strength = clamp(ColorModulator.x, 0.0, 1.0);
    float alpha = daylight * ColorModulator.z * horizon * sideFade * edge * smoothstep(0.0, 0.12, strength)
        * mix(0.48, 0.76, strength);
    if (alpha < 0.001) discard;
    ivec2 size = textureSize(Sampler0, 0);
    int column = clamp(int(floor(clamp((acrossPixel - 1.0) / 10.0, 0.0, 0.9999) * float(size.x))), 0, size.x - 1);
    vec4 color = texelFetch(Sampler0, ivec2(column, 0), 0);
    // Keep the palette's order and hue while matching the soft pastel reference,
    // independent of whether the sun is lighting a pale or a dark sky.
    vec3 pastel = mix(color.rgb, vec3(0.78, 0.88, 1.0), 0.70);
    fragColor = vec4(pastel, color.a * alpha);
}
