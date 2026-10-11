package naturality.client.mixin;

import com.mojang.blaze3d.platform.NativeImage;
import java.util.List;
import java.util.Optional;
import naturality.client.snow.SnowSpritePixels;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.resources.metadata.animation.AnimationMetadataSection;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.client.resources.metadata.texture.TextureMetadataSection;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.metadata.MetadataSectionType.WithValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SpriteContents.class)
public abstract class SnowSpritePixelsMixin implements SnowSpritePixels {
    @Unique private Optional<AnimationMetadataSection> naturality$animation=Optional.empty();
    @Unique private Optional<TextureMetadataSection> naturality$texture=Optional.empty();
    @Override @Accessor("originalImage") public abstract NativeImage naturality$image();
    @Override @Accessor("byMipLevel") public abstract NativeImage[] naturality$mips();
    @Override public Optional<AnimationMetadataSection> naturality$animation() { return naturality$animation; }
    @Override public Optional<TextureMetadataSection> naturality$textureMetadata() { return naturality$texture; }
    @Inject(method="<init>(Lnet/minecraft/resources/Identifier;Lnet/minecraft/client/resources/metadata/animation/FrameSize;Lcom/mojang/blaze3d/platform/NativeImage;Ljava/util/Optional;Ljava/util/List;Ljava/util/Optional;)V",at=@At("TAIL"))
    private void naturality$metadata(Identifier name,FrameSize size,NativeImage image,Optional<AnimationMetadataSection> animation,
            List<WithValue<?>> extra,Optional<TextureMetadataSection> texture,CallbackInfo ci) {
        naturality$animation=animation;naturality$texture=texture;
    }
}
