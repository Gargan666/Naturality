NATURALITY - RESOURCE DROP FOLDER
================================

Put finished assets here. Ctrl+Shift+B in VS Code (or gradlew build)
automatically packages them in the mod JAR under assets/naturality/.
The development client and VS Code debug resource task use this folder too.
Do not copy files into build/ or duplicate them under src/main/resources.

WHERE FILES GO
  textures/block/     Block textures (.png)
  textures/item/      Item textures (.png)
  textures/entity/    Entity textures (.png)
  textures/particle/  Particle textures (.png)
  textures/gui/       Interface textures (.png)
  sounds/            Audio clips (.ogg, encoded as Ogg Vorbis)
  sounds.json        Sound events and the clips each event plays

Use lowercase filenames with underscores, without spaces. Subfolders are OK.
Keep editable source files (PSD, Aseprite, WAV masters, etc.) elsewhere.
README.txt and .gitkeep files are excluded from the build.

TEXTURES
Use PNG. Ordinary block/item textures commonly start at 16 x 16 pixels.
Animation metadata can sit beside its PNG as filename.png.mcmeta.
Example: resources/textures/block/mossy_stone.png
  JAR: assets/naturality/textures/block/mossy_stone.png
  Model reference: naturality:block/mossy_stone
Adding a texture alone does not register a new block, item or particle.

SOUNDS
Export as Ogg Vorbis (.ogg), not Ogg Opus. Renaming an MP3/WAV is not conversion.
Use MONO for sounds located in the world, so positioning and distance fading
work correctly. Stereo is suitable for non-positional music/UI ambience.
44.1 kHz or 48 kHz are practical export sample rates.

Example: resources/sounds/portal/open.ogg
  JAR: assets/naturality/sounds/portal/open.ogg
  Sound file reference: naturality:portal/open (no sounds/ prefix or .ogg)

TO REPLACE THE EXISTING PORTAL OPENING SOUND
1. Create sounds/portal/ and put your mono open.ogg there.
2. In resources/sounds.json, replace the portal_open sounds array with:
     "sounds": [ { "name": "naturality:portal/open", "type": "file" } ]
3. Keep its subtitle and event name. Build and restart the development client.
The existing definition uses Minecraft's End portal sound until you change it.

New events can be added as additional entries in sounds.json, but the mod must
also call those events to play them; dropping an audio file here does not make
it play automatically. Other resource JSON files remain in src/main/resources.

See AGENTS.md in the project root for the current project map and working rules.

CHORUS FLOWER MODEL
Edit block models/chorus_flower_bloom.json in this folder. Each build and
Gradle development launch generates assets/naturality/models/block/chorus_flower_bloom.json
from that export. VS Code's prepare debug resources task does this too.
The build remaps resources: texture references to naturality:, repairs the
export's unresolved particle alias and omits unassigned #missing faces.
Both vanilla living/dead flower model overrides inherit the generated model.
After editing, rebuild/relaunch; F3+T alone does not run the build conversion.
Both chorus_flower.json and chorus_flower_bloom.json exports are now imported
automatically. Placed flowers choose normal/bloom at weights 9:1 across all ages;
the inventory item uses the normal model. Edit either export and rebuild/relaunch.
Chorus flowers now store facing and bloom in their game block state. New flowers
roll bloom once at 10%; that saved choice controls both the model and hitbox.
The earlier render-only 9:1 weighted blockstate has been replaced. All six model
orientations are mapped automatically through blockstate rotation; model export
edits still require rebuild/relaunch. Normal collision is 7/16 block along facing;
decorative leaf/hair planes do not collide.
Edit textures/block/chorus_plant.png to change the plant texture across vanilla
stems and custom flower models. The build maps this single source to both
minecraft and naturality texture namespaces, including optional PNG animation
metadata. Rebuild/relaunch after editing. Growing flowers and branches now
inherit their parent's normal/bloom variant; growth does not reroll it.
SMOOTH ENDSTONE
textures/block/smooth_endstone.png supplies all six faces of the registered
naturality:smooth_endstone block and its inventory item. Edit and rebuild/relaunch
as usual. Find Smooth Endstone beside End Stone in the Building Blocks creative tab.
LIRESTONE
textures/block/lirestone.png supplies all six faces of naturality:lirestone. Edit and
rebuild/relaunch. Lirestone appears beside Smooth End Stone in Building Blocks.

Starfall assets:
textures/entity/star.png supplies the static two-cube star model; the current
geometry follows entity models/star.java. Both stars and fizzles share it.
textures/particle/star_trail.png supplies the attached velocity-aligned trail.
Replace these PNGs in place to change visuals. The trail is anchored 76% down
the image; artwork should extend mainly above that anchor, behind travel.
Fizzle burst particles currently use vanilla END_ROD and POOF placeholders.

CHORUS SPROUT
textures/block/chorus_sprout.png supplies the untinted crossed-plane foliage
and inventory sprite for naturality:chorus_sprout. Edit and rebuild/relaunch.
Find Chorus Sprout beside Short Grass in the Natural Blocks creative tab.
Naturally generated chorus plants now scatter up to four Chorus Sprouts within
two blocks of their bases, on nearby End Stone or Lirestone. This affects fresh
chunk generation after restarting with the rebuilt mod.

LIRESTONE SOUNDS
sounds/blocks/lirestone/break1.ogg through break4.ogg supply break/mining sounds;
place1.ogg through place4.ogg supply placement; step1.ogg through step6.ogg supply
footsteps/landing. resources/sounds.json pools the variants. Edit and rebuild/relaunch.
Lirestone OGG clips are mono (one channel, 44100 Hz); preserve mono when exporting replacements for positional block audio.

SMOOTH LIRESTONE
textures/block/smooth_lirestone.png supplies all six block faces and the block
item for naturality:smooth_lirestone. Uses Lirestone's custom sounds. Edit and
rebuild/relaunch. Find it immediately after Lirestone in Building Blocks.

ECHO CORAL
textures/block/echo_coral.png supplies every face and inventory item of Echo Coral.
sounds/blocks/echo_coral supplies break1-3, place1-4 and step1-4 mono OGG variants.
Mining hits reuse break, landing reuses step. Edit and rebuild/relaunch as usual.
Find Echo Coral after Brown Mushroom Block in Natural Blocks.

LYREN HEATH
textures/block/lyren_heath.png supplies the sides and lyren_heath_top.png the top.
Bottom uses vanilla End Stone. Uses standard stone sounds. Edit and rebuild/relaunch.

MOSS
textures/block/moss.png supplies the thin glow-lichen-style surface block and
inventory sprite for naturality:moss. Uses vanilla moss sounds. Edit and rebuild.


CLOVERS
textures/block/clovers.png and clovers_stem.png supply wildflower-style foliage.
Both model pieces receive biome foliage tint. Edit and rebuild/relaunch.


FOUR LEAF CLOVER
textures/item/four_leaf_clover.png supplies the rare Clovers drop and Luck brewing ingredient.

