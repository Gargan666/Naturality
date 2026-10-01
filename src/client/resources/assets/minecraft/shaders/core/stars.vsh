#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:dynamictransforms.glsl>
#include <minecraft:projection.glsl>
#include <minecraft:globals.glsl>
#include <naturality:feature_settings.glsl>

layout(location = 0) in vec3 Position;
layout(location = 0) flat out float starBrightness;
layout(location = 1) flat out float starSkyHeight;

float randomStar(uint seed) {
    seed = (seed ^ (seed >> 16u)) * 0x7feb352du;
    seed = (seed ^ (seed >> 15u)) * 0x846ca68bu;
    return float(seed ^ (seed >> 16u)) / 4294967295.0;
}

void main() {
    uint seed = uint(gl_VertexIndex / 4) * 17u;
    const float TAU = 6.28318530718;
    // GameTime wraps each 1200 seconds. Integer cycle counts keep that seam smooth.
    float phase = TAU * randomStar(seed + 1u);
    float cycles = floor(mix(80.0, 200.0, randomStar(seed + 2u)));
    float wave = sin(TAU * GameTime * cycles + phase);
    float slowWave = sin(TAU * GameTime * floor(mix(29.0, 65.0, randomStar(seed + 3u))) + phase * 2.3);
    float pulse = 0.5 + 0.5 * (0.85 * wave + 0.15 * slowWave);
    float baseBrightness = mix(0.55, 1.0, randomStar(seed + 4u));
    float minimum = mix(0.12, 0.30, randomStar(seed + 5u));
    // A sparse bright tail lets a few stars briefly reach white at their peaks.
    float peak = 1.0 + 0.8 * pow(randomStar(seed + 10u), 8.0);
    starBrightness = baseBrightness * mix(minimum, peak, pulse);

    float sizeWave = sin(TAU * GameTime * floor(mix(60.0, 150.0, randomStar(seed + 6u))) + phase + 0.4);
    float size = mix(0.15, 0.25, randomStar(seed + 7u))
        * (1.0 + mix(0.04, 0.09, randomStar(seed + 8u)) * sizeWave);
    if (!NATURALITY_STAR_PULSES) {
        starBrightness = 0.5;
        size = mix(0.15, 0.25, randomStar(seed + 7u));
    }
    vec2 corners[4] = vec2[](vec2(1, -1), vec2(1, 1), vec2(-1, 1), vec2(-1, -1));
    vec2 corner = corners[gl_VertexIndex % 4] * size;
    float angle = TAU * randomStar(seed + 9u);
    corner = mat2(cos(angle), sin(angle), -sin(angle), cos(angle)) * corner;
    vec3 inward = -normalize(Position);
    vec3 right = normalize(cross(vec3(0, 1, 0), inward));
    vec3 up = cross(inward, right);
    vec3 position = Position + right * corner.x + up * corner.y;
    vec4 clipPosition = ProjMat * ModelViewMat * vec4(position, 1.0);
    // Height on the sky sphere, before camera projection. The lower 40% of a
    // sphere spans normalized vertical coordinates -1.0 through -0.2.
    float starAngle = ColorModulator.r;
    float rotatedSkyY = cos(starAngle) * Position.y - sin(starAngle) * Position.z;
    starSkyHeight = rotatedSkyY / length(Position);
    gl_Position = clipPosition;
}
