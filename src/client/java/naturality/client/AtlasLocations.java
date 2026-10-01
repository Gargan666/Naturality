package naturality.client;

import net.minecraft.resources.Identifier;

/** Texture locations for APIs which still address atlases by their texture path. */
public final class AtlasLocations {
    public static final Identifier BLOCKS = Identifier.withDefaultNamespace("textures/atlas/blocks.png");
    public static final Identifier PARTICLES = Identifier.withDefaultNamespace("textures/atlas/particles.png");

    private AtlasLocations() { }
}
