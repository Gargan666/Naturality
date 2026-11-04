Golden effect masks

golden/minecraft/ mirrors the original texture paths beneath textures/.
For example, golden/minecraft/entity/sheep/sheep_wool.png controls adult sheep,
and golden/minecraft/block/white_stained_glass.png controls golden glass.
Equipment folders include leather armor, horse armor, wolf armor and llama carpets.
Baby models and separate item sprites have their own masks when their textures differ.
Models sharing a texture also share its mask (for example stairs and full blocks).
These are exports of the existing coverage, not newly painted designs.

The two existing harness masks retain their original paths:

golden_harness.png: 128 x 128, matching Minecraft's white harness equipment texture.
golden_harness_item.png: 16 x 16, matching the white harness item texture.

Edit them without resizing or moving the texture coordinates.
White pixels apply the golden effect. Black or transparent pixels preserve the
white variant's texture without the effect. Masks are binary: alpha and red
must both be at least 128 to enable the effect. Gray does not set effect strength.
The source texture's transparency is always preserved.

The white variant supplies the underlying texture regardless of mask coverage.
Rebuild resources after editing these source files, then reload game resources.
Resource packs can override any mask using its path beneath assets/naturality/.
For a new source texture, add textures/masks/golden/<namespace>/<source path
without textures/>. The harness keeps its two explicit paths above.
If a mask is absent, automatic white/black dye coverage is used instead.
An explicitly supplied golden texture takes precedence over runtime fallback.
Golden wool (including its carpet, stairs and slabs) uses the white_wool.png mask.
The original golden wool artist input is retained but no longer packaged.
Supplied golden dye item artwork does not use these procedural masks.
Masks only control the existing gold-effect texture layers; they cannot extend a
layer into transparent pixels or onto another model part with a separate texture.
