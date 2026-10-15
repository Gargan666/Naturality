Maddened Creeper model
---------------------
Edit and re-export maddened_creeper.java from Blockbench using Mojang mappings.
The Gradle build automatically copies its createBodyLayer() geometry into a
generated Minecraft 26.3 geometry class; no manual Java copying is needed.
Geometry, UVs, texture dimensions and part transforms come from this export.
The old exported setupAnim/renderToBuffer methods are ignored; the mod supplies
walking/head animation and the idle/explosion pulse.

Keep Body as a root child, with Head and leg0, leg1, leg2, leg3 beneath Body.
These names and hierarchy are used by the animation wrapper.

Run gradlew assemble to rebuild the JAR, then restart Minecraft with that JAR.
prepareDebugLaunch also regenerates/compiles the model for development launches.
There is no model-file hot reload in a running game.

Textures live under resources/textures/entity:
  maddened_creeper.png          normal body texture
  maddened_creeper_emissive.png full-bright additive detail layer
Both use the same UV layout. Use black or transparent pixels where no glow is
wanted. The glowing layer follows the model's pose and explosion pulse.

Maddened Zombie model
--------------------
The same automatic build generation handles maddened_zombie.java. Keep Body,
Head, RightArm, LeftArm, RightLeg and LeftLeg as root children. Their names are
mapped to Minecraft's humanoid names; an empty hat child is added for standard
zombie rendering. Geometry and UVs come from the export. Walking, melee, held
items and armor use the normal zombie animation/rendering system, with a scaled
baby version of the same custom model. Texture: textures/entity/maddened_zombie.png.
