WEATHER CLUSTER PLACEHOLDERS
============================
Replace the six PNGs here without changing their names, then rebuild:
  rain_cluster.png, rain_cluster_1.png, rain_cluster_2.png
  snow_cluster.png, snow_cluster_1.png, snow_cluster_2.png

For a running
development client, processResources and reload resources (F3+T).

Each transparent PNG is ONE cluster card, not an animation strip or one droplet.
The placeholders are 128x128 RGBA with 25-50 scattered droplets/flakes per image.
Use a transparent background and leave a transparent margin around all edges.
Keep rain streaks vertical (texture Y): the renderer locks this axis to the wind-tilted falling velocity and turns around it toward the viewer position. Snow may use scattered dots or small snowflakes;
its complete card sways while preserving the velocity axis lock. White/grayscale artwork is
recommended; rain receives the local biome water tint and snow remains white under world light.
Square replacement resolutions are supported; changing resolution does not
change the world size of the card (approximately 3.6-4.6 blocks per side).

For additional randomly chosen cluster layouts, add filenames to:
src/client/resources/assets/naturality/particles/rain_cluster.json
src/client/resources/assets/naturality/particles/snow_cluster.json
The textures lists select variants, not animation frames. No Java changes needed.

Each variant is equally likely; each card has a 50% horizontal flip chance.
Rain variants: fine short streaks, long thick streaks, paired broken streaks.
Snow variants: small square flakes, crosses, small paired flakes.



