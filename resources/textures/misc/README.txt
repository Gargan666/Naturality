SUN FLARE SPRITES
----------------
sun_flare_0.png through sun_flare_5.png are the six camera-flare elements,
ordered from nearest the sun to the final, largest element. Each starts at
16x16 pixels. Edit the files directly; do not regenerate them during builds.

Use grayscale RGB and transparency: white gives full intensity, gray dims it,
black or alpha=0 is invisible. The supplied files use white RGB with varying
alpha to preserve the previous procedural shapes exactly to 8-bit precision.
In-game opacity multiplies grayscale brightness by alpha, then applies the
camera-flare setting, visibility and viewing-angle fades. In-game tint supplies
daytime colors, colored rims, and sunrise/sunset warmth.

You can change each PNG's resolution independently. Use square PNGs to keep
square pixels; screen size/position are separate from texture resolution.
Nearest texel sampling keeps the artwork crisp while its quad moves smoothly.

Run gradlew processResources (or build/prepareDebugLaunch) after editing these
source files, then reload resources with F3+T in a development game. A packaged
mod must be rebuilt, or overridden with a resource pack under
assets/naturality/textures/misc/sun_flare_0.png (and so on).
