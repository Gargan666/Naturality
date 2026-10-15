#ifndef NATURALITY_MENU_GEOMETRY
#define NATURALITY_MENU_GEOMETRY
layout(std140) uniform MenuRegion {
    vec4 MenuBounds;
    vec4 MenuEye;
    vec4 MenuSurfaceGrid;
    vec4 MenuView;
};
layout(std140) uniform MenuFloorsLow { uvec4 MenuFloorLow[3600]; };
layout(std140) uniform MenuFloorsHigh { uvec4 MenuFloorHigh[3600]; };
float naturality_menu_floor(vec2 position) {
    ivec2 cell = clamp(ivec2(floor(position - MenuSurfaceGrid.xy)), ivec2(0), ivec2(239));
    int index = cell.y * 240 + cell.x;
    int local = index % 28800;
    int word = local / 2;
    uint packed = index < 28800 ? MenuFloorLow[word / 4][word % 4] : MenuFloorHigh[word / 4][word % 4];
    return float((packed >> uint((local % 2) * 16)) & 65535u) + MenuSurfaceGrid.z;
}
float naturality_menu_cover_side(vec2 position) {
    vec2 towardCamera = MenuEye.xz - MenuBounds.xy;
    float distance = length(towardCamera);
    if (distance < 0.001) return 0.0;
    float nearSide = dot(position - MenuBounds.xy, towardCamera / distance);
    // Stay entirely on the camera's half. Ease over two blocks on that half
    // so shared vertices move continuously as the camera orbits the model.
    return smoothstep(0.0, 2.0, nearSide);
}
vec2 naturality_menu_extension(vec3 worldPosition) {
    if (MenuBounds.w == 0.0 || MenuView.w <= 0.0) return vec2(0.0);
    vec2 delta = worldPosition.xz - MenuBounds.xy;
    float distance = length(delta);
    // Two hidden blocks keep every affected terrain face beyond complete fog.
    // Equal world vertices receive equal offsets, avoiding cracks between chunks.
    float extension = min(768.0, max(0.0, distance - MenuBounds.w - 2.0) * 64.0);
    return delta * (extension * naturality_menu_cover_side(worldPosition.xz) / max(distance, 0.001));
}
// Stretch only the fully fogged support row, keeping its top joined to the surface.
float naturality_menu_wall_drop(vec3 worldPosition) {
    if (MenuBounds.w == 0.0 || MenuView.w <= 0.0
            || length(worldPosition.xz - MenuBounds.xy) <= MenuBounds.w + 2.0) return 0.0;
    float floorY = max(max(naturality_menu_floor(worldPosition.xz + vec2(.02,.02)),
                          naturality_menu_floor(worldPosition.xz + vec2(-.02,.02))),
                       max(naturality_menu_floor(worldPosition.xz + vec2(.02,-.02)),
                           naturality_menu_floor(worldPosition.xz - vec2(.02,.02))));
    return worldPosition.y < floorY - .5 ? 768.0 * naturality_menu_cover_side(worldPosition.xz) : 0.0;
}
#endif
