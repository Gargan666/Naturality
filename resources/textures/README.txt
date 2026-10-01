NATURALITY - TEXTURE DROP FOLDER
==============================

Put finished PNG textures in the folders here:

  block/   Block textures, such as mossy_stone.png
  item/    Item textures, such as forest_gem.png
  entity/  Entity textures, organized in subfolders if needed
  particle/ Particle textures, such as leaf.png
  gui/     Interface textures

Use lowercase names with underscores instead of spaces, for example:
  block/mossy_stone.png
  item/forest_gem.png

Use PNG for finished textures. A 16 x 16 image is a useful starting point
for ordinary block and item textures. Other dimensions depend on the model
or renderer. Keep editable source files (PSD, Aseprite, etc.) elsewhere;
files placed here are packaged into the mod, except README.txt and .gitkeep.

Press Ctrl+Shift+B in VS Code to build. Gradle copies this folder into:
  assets/naturality/textures/
inside the built mod JAR. You do not need to copy textures into src or build.
Treat this folder as the source of truth for Naturality textures; do not
also put a texture at the same resource path under src/main/resources.

Example:
  Your file: resources/textures/block/mossy_stone.png
  JAR path:  assets/naturality/textures/block/mossy_stone.png
  Model texture reference: naturality:block/mossy_stone

A PNG alone does not add a block or item to Minecraft. New content also
needs Java registration and the appropriate resource JSON/model definitions.
Entity and GUI renderers may refer to full texture paths, for example:
  naturality:textures/entity/example.png

Restart the development client after rebuilding to see updated resources.
For more project details, see PROJECT_REFERENCE.txt in the project root.
