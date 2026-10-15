layout(std140) uniform NaturalityFluids {
    vec4 NaturalityFluidBounds[4];
    ivec4 NaturalityFluidInfo; // still/flow palette counts, palette row stride, enabled mask
    vec4 NaturalityLodBounds; // original tiles; average twins are one region-width to the right
};
