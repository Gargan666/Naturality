#ifndef NATURALITY_FOLIAGE_WIND
#define NATURALITY_FOLIAGE_WIND
layout(std140) uniform NaturalityWind { vec4 NaturalityWindValue; };
vec2 naturality_wind_offset(vec3 Position, ivec3 ChunkPosition, int foliageTag) {
    if (foliageTag >= 128 && foliageTag <= 159) foliageTag += 32;
    bool anchoredPlant = foliageTag >= 64 && foliageTag <= 99;
    bool attachedLeaf = foliageTag >= 160 && foliageTag <= 191;
    bool supportedVine = foliageTag >= 102 && foliageTag <= 117;
    bool connectedPlant = foliageTag == 100 || foliageTag == 101 || supportedVine;
    if (!(connectedPlant || anchoredPlant || attachedLeaf || (foliageTag >= 201 && foliageTag <= 240))) return vec2(0.0);
        // Sample on the world's 1/16-block grid, before camera subtraction.
        // Section origins are whole blocks; wrapping X/Z preserves the periodic
        // phase while retaining precision far from spawn (including negatives).
        vec3 worldPos = floor(Position * 16.0 + 0.5) / 16.0
            + vec3(ChunkPosition.x % 4096, ChunkPosition.y, ChunkPosition.z % 4096);
        if (attachedLeaf) {
            int anchor = foliageTag - 160;
            vec3 cellOffset = vec3(anchor & 1, anchor / 4, (anchor / 2) & 1);
            worldPos = floor(Position + 0.002) - cellOffset + 0.5
                + vec3(ChunkPosition.x % 4096, ChunkPosition.y, ChunkPosition.z % 4096);
        }
        float plantHeight = 0.0;
        if (anchoredPlant) {
            int anchor = foliageTag - 64;
            vec3 cellOffset = vec3(anchor % 3 - 1, anchor / 9 - 1, (anchor / 3) % 3 - 1);
            vec3 base = floor(Position + 0.002) - cellOffset;
            plantHeight = Position.y - base.y;
            worldPos = base + 0.5 + vec3(ChunkPosition.x % 4096, ChunkPosition.y, ChunkPosition.z % 4096);
        }
        float phase = dot(worldPos.xz, vec2(111.0, 85.0)) * (6.28318530718 / 4096.0) + worldPos.y * 0.08;
        float wave = sin(NaturalityWindValue.z * 1.256637 + phase) * 0.7
            + sin(NaturalityWindValue.z * 3.141593 + phase * 2.0) * 0.3;
        float weight = foliageTag == 101 ? 0.0 : connectedPlant ? 0.22 : (attachedLeaf || foliageTag == 240) ? 0.12 : anchoredPlant ? 0.22 : 0.22 * float(foliageTag - 201) / 19.0;
        vec2 offset = NaturalityWindValue.xy * wave * weight;
        // Faster, smaller gusts layer onto the broad bend. Their varying force
        // stays positive, so this overlay shake never pushes against the wind.
        float shake = smoothstep(0.80, 1.0, NaturalityWindValue.w);
        // As gust strength rises, move the broad sway's center halfway downwind
        // while retaining the original amplitude. The limiter below still
        // clips any residual motion against the current direction.
        wave = mix(wave, wave + 0.5, shake);
        float shakePhase = NaturalityWindValue.z * 16.755161 + phase * 2.3;
        float finePhase = NaturalityWindValue.z * 23.876104 + phase * 3.7
            + dot(worldPos.xz, vec2(-0.23, 0.31));
        float detailPhase = NaturalityWindValue.z * 29.530971 + phase * 1.3
            + dot(worldPos.xz, vec2(0.37, 0.19));
        float shakeAmount = 0.055 + sin(shakePhase) * 0.016
            + sin(finePhase) * 0.008 + sin(detailPhase) * 0.004;
        float windMagnitude = length(NaturalityWindValue.xy);
        vec2 windDirection = windMagnitude > 0.0001 ? NaturalityWindValue.xy / windMagnitude : vec2(0.0);
        offset += windDirection * shakeAmount * shake * (weight / 0.22);
        // Whole texture-pixel steps along world X/Z, never screen-space pixels.
        // Quantize displacement only, preserving model offsets and anchored roots.
        offset = sign(offset) * floor(abs(offset) * 16.0 + 0.5) / 16.0;
        // At high wind, clamp the combined slow bend and fast shake to the
        // downwind half-plane so neither motion can pull foliage backward.
        if (shake > 0.0 && windMagnitude > 0.0001) {
            float downwind = dot(offset, windDirection);
            if (downwind < 0.0) offset -= windDirection * downwind;
        }
        // Quantize one common tip displacement, then interpolate the anchored
        // bend. Subdivided overlay faces now lie exactly on the original plane.
        if (anchoredPlant) offset *= plantHeight;
        // Attachment directions point into supporting blocks. Clip only motion
        // toward those faces, after pixel quantization, including corner vines.
        if (supportedVine) {
            int supports = foliageTag - 102;
            if ((supports & 1) != 0) offset.x = max(offset.x, 0.0);
            if ((supports & 2) != 0) offset.x = min(offset.x, 0.0);
            if ((supports & 4) != 0) offset.y = max(offset.y, 0.0);
            if ((supports & 8) != 0) offset.y = min(offset.y, 0.0);
        }
    return offset;
}
#endif
