layout(std140) uniform NaturalityWaterSun {
    mat4 WaterSunInversePose;
    vec4 WaterSunBounds;
    vec4 WaterSunAppearance; // rain/cloud transmission, reserved
};
