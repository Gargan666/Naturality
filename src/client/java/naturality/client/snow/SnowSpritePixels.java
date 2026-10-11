package naturality.client.snow;

import com.mojang.blaze3d.platform.NativeImage;
import java.util.Optional;
import net.minecraft.client.resources.metadata.animation.AnimationMetadataSection;
import net.minecraft.client.resources.metadata.texture.TextureMetadataSection;

public interface SnowSpritePixels {
    NativeImage naturality$image();
    NativeImage[] naturality$mips();
    Optional<AnimationMetadataSection> naturality$animation();
    Optional<TextureMetadataSection> naturality$textureMetadata();
}
