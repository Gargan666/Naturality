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

