// Both border effects use the same virtual window and side fade.
float end_portal_side_alpha(float height) {
    return smoothstep(0.0, 0.75, height);
}

// Offset the depth plane in world units, not by a fixed device-depth increment.
// This keeps the separation stable with view angle, FOV and camera distance.
bool end_portal_window(vec3 position, float height, vec3 eye, vec2 cell,
        float separation, out float depth) {
    vec3 ray = position - eye;
    if (ray.y >= -0.00001) return false;
    float surfaceY = position.y + 0.75 - height;
    float t = (surfaceY - eye.y) / ray.y;
    if (t <= 0.0 || t > 1.0) return false;
    vec3 hit = eye + ray * t;
    if (any(lessThan(hit.xz, cell)) || any(greaterThanEqual(hit.xz, cell + 1.0))) return false;
    float separatedT = max(0.00001, (surfaceY + separation - eye.y) / ray.y);
    vec4 clip = ProjMat * ModelViewMat * vec4(eye + ray * separatedT, 1.0);
    depth = clip.z / clip.w;
    return true;
}
