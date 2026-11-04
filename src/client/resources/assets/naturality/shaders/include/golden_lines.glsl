#ifndef NATURALITY_GOLD_LINES
#define NATURALITY_GOLD_LINES
#include <naturality:golden_palette.glsl>
#include <naturality:distance_bands.glsl>

bool naturality_golden_marker(vec3 color) {
    return naturalityGoldenEnabled && all(lessThan(abs(color * 255.0 - vec3(249.0, 190.0, 58.0)), vec3(0.6)));
}

// Read this from the nearest level-zero texel, never from a filtered/mipped color.
// Green carries the source material's relative grayscale rank, not its absolute brightness.
bool naturality_golden_texel(vec3 color) {
    return naturalityGoldenEnabled && all(lessThan(abs(color.rb * 255.0 - vec2(249.0, 58.0)), vec2(0.6)));
}

float naturality_gold_band_width(int index) {
    float darkness = float(index) / float(max(1, naturalityGoldCount - 1));
    return 2.0 + floor(3.0 * (1.0 - darkness) + 0.5);
}

float naturality_gold_period() {
    float period = 0.0;
    int last = naturalityGoldCount - 1;
    for (int step = 0; step < 2 * last; step++) {
        period += naturality_gold_band_width(step <= last ? step : 2 * last - step);
    }
    return max(1.0, period);
}

// Evaluate view motion at the centre of the same pixel used for the pattern.
// Evaluating normalized view at each fragment splits a single pixel into bands.
// Capture all derivatives before branching on material or dye masks.
#ifdef NATURALITY_GOLD_FRAGMENT
vec4 naturality_gold_pixel_view(vec2 grid, vec3 relative) {
    vec2 dx = dFdx(grid), dy = dFdy(grid);
    vec2 delta = floor(grid) + 0.5 - grid;
    float determinant = dx.x * dy.y - dx.y * dy.x;
    vec2 screenOffset = abs(determinant) > 1e-12
        ? vec2(delta.x * dy.y - delta.y * dy.x, dx.x * delta.y - dx.y * delta.x) / determinant
        : vec2(0.0);
    vec3 rx = dFdx(relative), ry = dFdy(relative);
    vec3 centre = relative + rx * screenOffset.x + ry * screenOffset.y;
    vec3 normal = cross(rx, ry);
    vec3 phaseGradient = ((dx.x - dx.y) * cross(ry, normal)
        + (dy.x - dy.y) * cross(normal, rx)) / max(dot(normal, normal), 1e-20);
    // Compress the camera-relative phase, not atlas/section coordinates. This
    // preserves existing phase joins and avoids jumps at texture/chunk borders.
    float correction = dot(centre, phaseGradient)
        * (1.0 - 1.0 / naturality_band_distance_scale(centre));
    return vec4(centre / max(length(centre), 0.00001), correction);
}
#endif

// Terrain uses a physical 16-pixels-per-block grid, including thin pane edges.
// Atlas offsets, UV cropping and narrow faces cannot stretch the bands.
vec2 naturality_gold_surface_grid(vec3 position, vec3 normal) {
    vec3 n = abs(normal);
    if (n.y > max(n.x, n.z)) return position.xz * 16.0;
    return (n.x > n.z ? position.zy : position.xy) * 16.0;
}

// Contiguous integer-width bands walk the authored palette in order and reflect
// at each end. Adjacent bands always differ by exactly one palette entry.
float naturality_gold_line_index(vec2 pixel, vec4 view) {
    pixel = floor(pixel);
    float phase = pixel.x - pixel.y + floor(6.0 * (view.x - view.y)) - view.w;
    int last = naturalityGoldCount - 1;
    if (last == 0) return 0.0;
    float remaining = mod(phase, naturality_gold_period());
    // Bright entries occupy up to five pixels; the darkest entries only two.
    for (int step = 0; step < 2 * last; step++) {
        int index = step <= last ? step : 2 * last - step;
        float width = naturality_gold_band_width(index);
        if (remaining < width) return float(index);
        remaining -= width;
    }
    return 0.0;
}

vec3 naturality_gold_lines(vec2 pixel, vec4 view) {
    return naturalityGoldPalette[int(naturality_gold_line_index(pixel, view))];
}

vec3 naturality_gold_overlay(vec3 shadeRank, vec2 grid, vec4 pixelView) {
    float rank = clamp(dot(shadeRank, vec3(0.2126, 0.7152, 0.0722)), 0.0, 1.0);
    float last = float(naturalityGoldCount - 1);
    // Dark source shades travel down the ordered palette, never toward neutral black.
    float index = mix(last, naturality_gold_line_index(grid, pixelView), rank);
    int lower = int(floor(index));
    int upper = min(lower + 1, naturalityGoldCount - 1);
    return mix(naturalityGoldPalette[lower], naturalityGoldPalette[upper], fract(index));
}
#endif
