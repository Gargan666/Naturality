#ifndef NATURALITY_FOLIAGE_WIND
#define NATURALITY_FOLIAGE_WIND
#include <naturality:feature_settings.glsl>
float naturality_wind_detail(vec3 cameraRelative) {
    if (!NATURALITY_DISTANT_WIND) return 1.0;
    // Identical shared-edge coordinates give matching weights across blocks.
    return 1.0 - smoothstep(64.0 * 64.0, 96.0 * 96.0, dot(cameraRelative.xz, cameraRelative.xz));
}
struct NaturalityBrush { vec4 lower; vec4 upper; vec4 shape; };
layout(std140) uniform NaturalityWind {
    vec4 NaturalityWindValue;
    vec4 NaturalityWindShape;
    ivec4 NaturalityBrushCount;
    NaturalityBrush NaturalityBrushes[32];
    vec4 NaturalityBrushNodes[512];
};
vec3 naturality_brush_offset(vec3 Position, ivec3 ChunkPosition, int tag) {
    // Leaves and fitted snow retain their own geometry contracts.
    if (tag == 255 || tag == 240 || (tag >= 128 && tag <= 192)
            || (tag >= 224 && tag <= 231) || tag >= 245 || tag == 242) return vec3(0.0);
    vec3 relative = Position + vec3(ChunkPosition - NaturalityBrushCount.yzw);
    vec2 owner = floor(relative.xz + 0.002);
    bool anchored = tag >= 64 && tag <= 99;
    bool crop = tag >= 120 && tag <= 127;
    if (anchored) {
        int encoded = tag - 64;
        owner -= vec2(encoded % 3 - 1, (encoded / 3) % 3 - 1);
    } else if (crop) {
        int encoded = tag - 120;
        owner -= vec2(encoded & 1, (encoded >> 1) & 1);
    }
    vec3 offset = vec3(0.0);
    for (int i = 0; i < NaturalityBrushCount.x; i++) {
        NaturalityBrush brush = NaturalityBrushes[i];
        // Encoded plant roots identify their column even when model offsets
        // extend past a cell edge. Other strands stay within their exact column.
        if (anchored || crop) {
            if (any(notEqual(owner, brush.lower.xz))) continue;
        } else if (any(lessThan(relative.xz, brush.lower.xz - 0.0001))
                || any(greaterThan(relative.xz, brush.upper.xz + 0.0001))) continue;
        if (relative.y < brush.lower.y - 0.0626 || relative.y > brush.upper.y + 0.0001) continue;
        float height = clamp(relative.y - brush.lower.y, 0.0, brush.upper.w - 1.0);
        int first = int(brush.lower.w);
        int node = int(floor(height));
        int next = min(node + 1, int(brush.upper.w) - 1);
        // Both blocks meeting at a join read the same node, including its
        // vertical correction. A whole strand no longer shares one rigid bend.
        offset += mix(NaturalityBrushNodes[first + node].xyz,
            NaturalityBrushNodes[first + next].xyz, fract(height));
    }
    return offset;
}
vec2 naturality_leaf_wind_at(vec2 worldXZ) {
    if (all(equal(NaturalityWindValue.xy, vec2(0.0)))) return vec2(0.0);
    float phase = dot(worldXZ, vec2(111.0, 85.0)) * (6.28318530718 / 4096.0);
    float wave = sin(NaturalityWindValue.z * 1.256637 + phase) * 0.7
        + sin(NaturalityWindValue.z * 3.141593 + phase * 2.0) * 0.3;
    float shake = NaturalityWindShape.z;
    wave = mix(wave, 1.0 + wave * 0.12, shake);
    float weight = 0.12;
    vec2 offset = NaturalityWindValue.xy * wave * weight;
    float windMagnitude = NaturalityWindShape.w;
    vec2 windDirection = NaturalityWindShape.xy;
    offset = sign(offset) * floor(abs(offset) * 16.0 + 0.5) / 16.0;
    if (shake > 0.0 && windMagnitude > 0.0001) {
        float downwind = dot(offset, windDirection);
        if (downwind < 0.0) offset -= windDirection * downwind;
    }
    if (shake > 0.0) {
        float shakePhase = NaturalityWindValue.z * 16.755161 + phase * 2.3;
        float finePhase = NaturalityWindValue.z * 23.876104 + phase * 3.7
            + dot(worldXZ, vec2(-0.23, 0.31));
        float detailPhase = NaturalityWindValue.z * 29.530971 + phase * 1.3
            + dot(worldXZ, vec2(0.37, 0.19));
        float shakeForward = sin(shakePhase) * 0.09 + sin(finePhase) * 0.05 + sin(detailPhase) * 0.025;
        float shakeSide = sin(finePhase * 0.73 + shakePhase * 0.19) * 0.03
            + sin(detailPhase * 0.61 + shakePhase * 0.37) * 0.015;
        vec2 crosswind = vec2(-windDirection.y, windDirection.x);
        offset += (windDirection * shakeForward + crosswind * shakeSide) * shake * (weight / 0.22);
    }
    return sign(offset) * floor(abs(offset) * 16.0 + 0.5) / 16.0;
}
vec2 naturality_wind_offset(vec3 Position, ivec3 ChunkPosition, int foliageTag) {
    if (all(equal(NaturalityWindValue.xy, vec2(0.0)))) return vec2(0.0);
    // Vine planes sit 1/16 inside their cell; the attachment belongs to the
    // adjacent leaf's exact block-edge vertex, not that inset plane.
    if (foliageTag == 232) return naturality_leaf_wind_at(round(Position.xz)
        + vec2(ChunkPosition.x % 4096, ChunkPosition.z % 4096));
    // The same travelling gust flows down hanging strands and up rising strands. Depth controls the
    // attachment envelope; world Y keeps long strands and section joins in phase.
    bool leafHanging = foliageTag == 118 || (foliageTag >= 233 && foliageTag <= 239);
    bool rising = foliageTag >= 201 && foliageTag <= 208;
    if (rising || leafHanging || (foliageTag >= 193 && foliageTag <= 200)) {
        float depth = float(leafHanging ? (foliageTag == 118 ? 0 : foliageTag - 232) : rising ? foliageTag - 201 : foliageTag - 193);
        vec2 worldXZ = floor(Position.xz * 16.0 + 0.5) / 16.0
            + vec2(ChunkPosition.x % 4096, ChunkPosition.z % 4096);
        float worldY = Position.y + float(ChunkPosition.y);
        if (rising) worldY = -worldY; // Mirror the hanging wave through its attachment plane.
        float phase = dot(worldXZ, vec2(111.0,85.0)) * (6.28318530718 / 4096.0);
        float time = NaturalityWindValue.z;
        float wave = sin(time * 1.256637 + worldY * 0.55 + phase) * 0.7
            + sin(time * 2.31 + worldY * 0.91 + phase * 2.0) * 0.3;
        float severe = NaturalityWindShape.z;
        float bend = mix(wave,1.0 + wave * 0.18,severe);
        float envelope = (1.0 - exp(-depth * 0.45)) * 1.6;
        vec2 flow = NaturalityWindValue.xy * bend * 0.22;
        vec2 direction = NaturalityWindShape.xy;
        flow += direction * (sin(time * 9.1 + worldY * 1.4 + phase) * 0.065
            + sin(time * 13.7 + worldY * 0.8 + phase * 2.0) * 0.025) * severe;
        flow *= envelope; // Smooth strand motion; the root envelope keeps the attachment fixed.
        if (leafHanging) {
            // Interpolate the supporting leaf's corner motion at the attachment
            // plane instead of sampling a different phase at the vine center.
            vec2 cell = floor(worldXZ);
            vec2 local = fract(worldXZ);
            vec2 a = mix(naturality_leaf_wind_at(cell),naturality_leaf_wind_at(cell+vec2(1,0)),local.x);
            vec2 b = mix(naturality_leaf_wind_at(cell+vec2(0,1)),naturality_leaf_wind_at(cell+vec2(1,1)),local.x);
            flow += mix(a,b,local.y) * (1.0 - smoothstep(0.0,7.0,depth));
        }
        return flow;
    }
    // One shared root phase bends the entire stalk. The first segment resists
    // strongly; each successive segment gains bend without opening its joins.
    if (foliageTag >= 0 && foliageTag <= 63) {
        float height = float(foliageTag);
        vec2 root = floor(Position.xz + 0.002) + 0.5
            + vec2(ChunkPosition.x % 4096, ChunkPosition.z % 4096);
        vec2 sway = naturality_leaf_wind_at(root);
        return sway * (height * height / (height + 2.0));
    }
    // Snow-on-leaf side overlays are subdivided to follow texture pixels. Their
    // vertices must interpolate the original leaf-face corner offsets instead
    // of independently sampling the nonlinear wind field at every subdivision.
    if (foliageTag >= 224 && foliageTag <= 231) {
        int encoded = foliageTag - 224;
        bool zFace = encoded >= 4;
        int anchor = encoded & 3;
        vec2 localAnchor = vec2(anchor & 1, (anchor >> 1) & 1);
        vec2 base = floor(Position.xz + 0.002) - localAnchor;
        vec2 local = clamp(Position.xz - base, 0.0, 1.0);
        vec2 section = vec2(ChunkPosition.x % 4096, ChunkPosition.z % 4096);
        if (zFace) {
            float z = base.y + localAnchor.y + section.y;
            vec2 a = naturality_leaf_wind_at(vec2(base.x + section.x, z));
            vec2 b = naturality_leaf_wind_at(vec2(base.x + 1.0 + section.x, z));
            return mix(a, b, local.x);
        }
        float x = base.x + localAnchor.x + section.x;
        vec2 a = naturality_leaf_wind_at(vec2(x, base.y + section.y));
        vec2 b = naturality_leaf_wind_at(vec2(x, base.y + 1.0 + section.y));
        return mix(a, b, local.y);
    }
    int cropAnchor = foliageTag - 120;
    bool anchoredCrop = foliageTag >= 120 && foliageTag <= 127;
    if (anchoredCrop) foliageTag = 64;
    else if (foliageTag >= 128 && foliageTag <= 159) foliageTag += 32;
    bool anchoredPlant = foliageTag >= 64 && foliageTag <= 99;
    bool attachedLeaf = foliageTag >= 160 && foliageTag <= 191;
    bool supportedVine = foliageTag >= 102 && foliageTag <= 117;
    bool connectedPlant = foliageTag == 100 || foliageTag == 101 || supportedVine;
    if (!(connectedPlant || anchoredPlant || attachedLeaf || foliageTag == 240)) return vec2(0.0);
        // Sample on the world's 1/16-block grid, before camera subtraction.
        // Section origins are whole blocks; wrapping X/Z preserves the periodic
        // phase while retaining precision far from spawn (including negatives).
        vec3 worldPos = floor(Position * 16.0 + 0.5) / 16.0
            + vec3(ChunkPosition.x % 4096, ChunkPosition.y, ChunkPosition.z % 4096);
        // Leaves and snow on leaves share the exact quantized world vertex,
        // so neighboring blocks deform together without outward expansion.
        if (attachedLeaf) worldPos = floor(Position * 16.0 + 0.5) / 16.0
            + vec3(ChunkPosition.x % 4096, ChunkPosition.y, ChunkPosition.z % 4096);
        float plantHeight = 0.0;
        if (anchoredPlant) {
            int anchor = foliageTag - 64;
            vec3 cellOffset = vec3(anchor % 3 - 1, anchor / 9 - 1, (anchor / 3) % 3 - 1);
            if (anchoredCrop) cellOffset = vec3(cropAnchor & 1, (cropAnchor >> 2) - 1, (cropAnchor >> 1) & 1);
            vec3 base = floor(Position + 0.002) - cellOffset;
            if (anchoredCrop) base.y -= 0.0625;
            plantHeight = Position.y - base.y;
            worldPos = base + 0.5 + vec3(ChunkPosition.x % 4096, ChunkPosition.y, ChunkPosition.z % 4096);
        }
        // A leaf's cap and side coating can sit at a different Y than the leaf
        // surface. Keep their wind phase independent of height so matching X/Z
        // vertices receive exactly the same deformation and do not clip apart.
        float phase = dot(worldPos.xz, vec2(111.0, 85.0)) * (6.28318530718 / 4096.0)
            + (attachedLeaf ? 0.0 : worldPos.y * 0.08);
        float wave = sin(NaturalityWindValue.z * 1.256637 + phase) * 0.7
            + sin(NaturalityWindValue.z * 3.141593 + phase * 2.0) * 0.3;
        float weight = foliageTag == 101 ? 0.0 : connectedPlant ? 0.22 : (attachedLeaf || foliageTag == 240) ? 0.12 : 0.22;
        // Faster, stronger gusts layer onto the broad bend once wind becomes severe.
        float shake = NaturalityWindShape.z;
        // At severe wind, foliage stays stretched near its full downwind bend;
        // only a narrow oscillation remains around that sustained pose.
        wave = mix(wave, 1.0 + wave * 0.12, shake);
        vec2 offset = NaturalityWindValue.xy * wave * weight;
        float windMagnitude = NaturalityWindShape.w;
        vec2 windDirection = NaturalityWindShape.xy;
        // Quantize and constrain the sustained bend before layering the shake.
        // This keeps the main pose pulled downwind while letting the violent
        // overlay swing freely across either side of that direction.
        offset = sign(offset) * floor(abs(offset) * 16.0 + 0.5) / 16.0;
        if (shake > 0.0 && windMagnitude > 0.0001) {
            float downwind = dot(offset, windDirection);
            if (downwind < 0.0) offset -= windDirection * downwind;
        }
        if (shake > 0.0) {
            float shakePhase = NaturalityWindValue.z * 16.755161 + phase * 2.3;
            float finePhase = NaturalityWindValue.z * 23.876104 + phase * 3.7
                + dot(worldPos.xz, vec2(-0.23, 0.31));
            float detailPhase = NaturalityWindValue.z * 29.530971 + phase * 1.3
                + dot(worldPos.xz, vec2(0.37, 0.19));
            float shakeForward = sin(shakePhase) * 0.09
                + sin(finePhase) * 0.05 + sin(detailPhase) * 0.025;
            float shakeSide = sin(finePhase * 0.73 + shakePhase * 0.19) * 0.03
                + sin(detailPhase * 0.61 + shakePhase * 0.37) * 0.015;
            vec2 crosswind = vec2(-windDirection.y, windDirection.x);
            offset += (windDirection * shakeForward + crosswind * shakeSide)
                * shake * (weight / 0.22);
        }
        // Whole texture-pixel steps along world X/Z, never screen-space pixels.
        // Quantize displacement only, preserving model offsets and anchored roots.
        offset = sign(offset) * floor(abs(offset) * 16.0 + 0.5) / 16.0;
        // Quantize one common tip displacement, then interpolate the anchored
        // bend. Subdivided overlay faces now lie exactly on the original plane.
        if (anchoredPlant) offset *= plantHeight;
        // Attachment directions point into supporting blocks. Clip only motion
        // toward those faces, after pixel quantization, including corner vines.
        if (supportedVine) {
            // Wall vines only flutter within a small fraction of a texture
            // pixel; the shared support mask keeps connected edges together.
            offset *= 0.04;
            int supports = foliageTag - 102;
            if ((supports & 1) != 0) offset.x = max(offset.x, 0.0);
            if ((supports & 2) != 0) offset.x = min(offset.x, 0.0);
            if ((supports & 4) != 0) offset.y = max(offset.y, 0.0);
            if ((supports & 8) != 0) offset.y = min(offset.y, 0.0);
        }
    return offset;
}
#endif
