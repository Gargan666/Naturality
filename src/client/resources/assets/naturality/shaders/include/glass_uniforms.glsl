#ifndef NATURALITY_GLASS_UNIFORMS
#define NATURALITY_GLASS_UNIFORMS
layout(std140) uniform NaturalityGlass {
    vec4 GlassBounds[34];
    vec4 GlassDimensions[34];
    vec4 GlassColors[34];
};
#endif
