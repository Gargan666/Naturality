layout(std140) uniform NaturalityFluids {
    vec4 NaturalityFluidBounds[4];
    ivec4 NaturalityFluidInfo; // still/flow palette counts, palette row stride, enabled mask
};
