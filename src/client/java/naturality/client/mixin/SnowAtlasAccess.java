package naturality.client.mixin;

import net.minecraft.client.renderer.texture.TextureAtlas;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(TextureAtlas.class)
public interface SnowAtlasAccess {
    @Accessor("width") int naturality$width();
    @Accessor("height") int naturality$height();
    @Accessor("maxMipLevel") int naturality$mipLevel();
}
