#version 330
#extension GL_ARB_separate_shader_objects : require
#include <minecraft:dynamictransforms.glsl>
uniform sampler2D Sampler0;
layout(location = 0) in vec2 curtainUv;
layout(location = 1) flat in vec3 curtainInfo;
layout(location = 2) in vec3 viewPosition;
layout(location = 0) out vec4 fragColor;
float hash(float x) { return fract(sin(x * 127.1 + 311.7) * 43758.5453); }
float noise(float x) {
    float cell = floor(x), t = fract(x);
    t = t*t*(3.0-2.0*t);
    return mix(hash(cell), hash(cell+1.0), t);
}
void main() {
    // Fixed world-sized cells: temporal color motion does not move or blur the pixel grid.
    vec2 cell = (floor(curtainUv / 3.0) + 0.5) * 3.0;
    float height = curtainInfo.g * 255.0;
    float v = clamp(cell.y / height, 0.0, 1.0);
    float phaseTime = ColorModulator.y;
    float strength = clamp(ColorModulator.x, 0.0, 1.0);
    float seed = curtainInfo.b * 6.2831853;
    float pulse = phaseTime * 1.5;
    float bend = 2.0 * sin(cell.x * .018 + pulse * .75 + seed);
    float broad = pow(noise(cell.x * .035 + .4*bend + 1.8*sin(pulse*1.1+seed)), 1.6);
    float threads = smoothstep(.48, .91, noise(cell.x * .21 + .5*bend + 1.2*sin(pulse*2.3+v*.9)));
    float columnLight = .22 + .46 * broad + .42 * threads;
    float crown = .18 * (1.0-noise(cell.x*.047+sin(pulse*.75+seed)));
    float vertical = smoothstep(crown, crown+.32, v) * (1.0 - smoothstep(.91, 1.0, v));
    float lowerRim = exp(-pow((v - .84 - .035 * sin(cell.x * .04 + pulse)) / .075, 2.0));
    // Broad illuminated regions travel along the fixed curtains, leaving dark gaps.
    float travel = cell.x * .0075 - phaseTime * .45 + seed;
    float spots = smoothstep(.17, .72, .5 + .5 * sin(travel + .55 * sin(cell.x * .0023 + seed)));
    spots *= .72 + .28 * noise(cell.x * .0056 - phaseTime * .17 + seed);
    float density = smoothstep(curtainInfo.r, curtainInfo.r + .24, ColorModulator.x);
    float ends = smoothstep(0.0, 96.0, cell.x) * (1.0-smoothstep(928.0, 1024.0, cell.x));
    float distanceFade = 1.0 - smoothstep(650.0, 1000.0, length(viewPosition));
    ivec2 paletteSize = textureSize(Sampler0, 0);
    float column = (cell.x / 420.0 + curtainInfo.b + .12 * sin(pulse*.3)) * float(paletteSize.x);
    int first = int(mod(floor(column), float(paletteSize.x)));
    int second = (first+1) % paletteSize.x;
    int row = clamp(int(floor(v * float(paletteSize.y))), 0, paletteSize.y-1);
    vec4 color = mix(texelFetch(Sampler0, ivec2(first,row),0), texelFetch(Sampler0, ivec2(second,row),0), fract(column));
    float alpha = color.a * vertical * (columnLight + lowerRim * .35) * (.025 + .975 * spots)
        * density * ends * distanceFade * strength * ColorModulator.z * ColorModulator.w;
    if (alpha < .001) discard;
    fragColor = vec4(color.rgb, alpha);
}
