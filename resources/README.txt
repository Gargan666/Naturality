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

See PROJECT_REFERENCE.txt in the project root for the rest of the project.

WEATHER PARTICLE PLACEHOLDERS
Replace textures/particle/rain_cluster{,_1,_2}.png and snow_cluster{,_1,_2}.png with transparent
square cluster artwork. Each image represents many droplets/flakes on one particle.
See textures/particle/README.txt and the project's PARTICLE_WEATHER.txt for details.


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
textures/block/lirestone.png supplies the four sides of naturality:lirestone.
textures/block/lirestone_top.png supplies both top and bottom. Edit either and
rebuild/relaunch. Lirestone appears beside Smooth End Stone in Building Blocks.

Starfall assets:
textures/entity/star.png supplies the static two-cube star model; the current
geometry follows entity models/star.java. Both stars and fizzles share it.
textures/particle/star_trail.png supplies the attached velocity-aligned trail.
Replace these PNGs in place to change visuals. The trail is anchored 76% down
the image; artwork should extend mainly above that anchor, behind travel.
Fizzle burst particles currently use vanilla END_ROD and POOF placeholders.
