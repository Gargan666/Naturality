#include <naturality:menu_geometry.glsl>
float linear_fog_value(float vertexDistance, float fogStart, float fogEnd);
bool naturality_menu_surface_visible(vec3 worldPosition, vec3 faceNormal) {
    if (MenuBounds.w == 0.0) return true;
    if (MenuView.w <= 0.0) return false;
    // Orient the derivative normal toward the parallel viewing rays. Downward
    // surfaces and the support shell must never become the model's underside.
    faceNormal *= dot(faceNormal, MenuView.xyz) < 0.0 ? -1.0 : 1.0;
    if (faceNormal.y < -0.01) return false;
    // Entirely fogged faces form the opaque outer cover, including its cut sides.
    if (length(worldPosition.xz - MenuBounds.xy) >= MenuBounds.w) return true;
    float inside = naturality_menu_floor((worldPosition - faceNormal * 0.02).xz);
    float outside = naturality_menu_floor((worldPosition + faceNormal * 0.02).xz);
    // Use the same generous surface band as packet extraction. A first-height
    // cutoff erased cliff recesses and ground beneath roofs even when meshed.
    return worldPosition.y >= min(inside, outside) - MenuEye.w + 0.01;
}
float naturality_menu_edge(vec3 worldPosition, vec3 faceNormal) {
    if (MenuBounds.w == 0.0) return 0.0;
    if (!naturality_menu_surface_visible(worldPosition, faceNormal)) return 1.0;
    // A single continuous horizontal envelope avoids height-grid patches.
    float edge = linear_fog_value(length(worldPosition.xz - MenuBounds.xy), MenuBounds.z, MenuBounds.w);
    return 1.0 - (1.0 - edge) * smoothstep(0.0, 1.0, MenuView.w);
}
float naturality_menu_cloud_edge(vec3 worldPosition) {
    if (MenuBounds.w == 0.0) return 0.0;
    return linear_fog_value(length(worldPosition.xz - MenuBounds.xy), MenuBounds.z * 4.0, MenuBounds.w * 4.0);
}
