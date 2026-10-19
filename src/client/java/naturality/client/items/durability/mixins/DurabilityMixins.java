package naturality.client.items.durability.mixins;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import naturality.client.items.durability.UselessItemRendering;
import naturality.client.items.durability.UselessSprites;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.SpriteLoader;
import net.minecraft.client.resources.metadata.animation.AnimationMetadataSection;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.client.resources.metadata.texture.TextureMetadataSection;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.metadata.MetadataSectionType.WithValue;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

public final class DurabilityMixins {
    private DurabilityMixins() {}

    @Mixin(SpriteContents.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class DurabilitySpritePixelsMixin implements UselessSprites.Pixels {
        @Unique private UselessSprites.@Nullable Source naturality$pixels;

        @Inject(method = "<init>(Lnet/minecraft/resources/Identifier;Lnet/minecraft/client/resources/metadata/animation/FrameSize;Lcom/mojang/blaze3d/platform/NativeImage;Ljava/util/Optional;Ljava/util/List;Ljava/util/Optional;)V", at = @At("RETURN"))
        private void naturality$capture(Identifier name, FrameSize size, NativeImage image,
                Optional<AnimationMetadataSection> animation, List<WithValue<?>> metadata,
                Optional<TextureMetadataSection> texture, CallbackInfo ci) {
            naturality$pixels = new UselessSprites.Source(image, animation, metadata, texture);
        }

        @Override public UselessSprites.Source naturality$source() { return Objects.requireNonNull(naturality$pixels); }
    }

    @Mixin(SpriteLoader.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class DurabilitySpriteLoaderMixin {
        @SuppressWarnings("null") // SpriteLoader's constructor initializes the shadow field.
        @Shadow @Final private Identifier location;

        @ModifyVariable(method = "stitch", at = @At("HEAD"), argsOnly = true)
        private List<SpriteContents> naturality$variants(List<SpriteContents> sprites) {
            return UselessSprites.expand(sprites, location);
        }
    }

    @Mixin(net.minecraft.client.renderer.item.CuboidItemModelWrapper.Unbaked.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class DurabilityItemModelMixin {
        @com.llamalad7.mixinextras.injector.ModifyReturnValue(method = "bake", at = @At("RETURN"))
        private net.minecraft.client.renderer.item.ItemModel naturality$brokenModel(
                net.minecraft.client.renderer.item.ItemModel intact,
                net.minecraft.client.renderer.item.ItemModel.BakingContext context,
                org.joml.Matrix4fc transformation) {
            return UselessItemRendering.bake(intact,
                (net.minecraft.client.renderer.item.CuboidItemModelWrapper.Unbaked)(Object)this, context, transformation);
        }
    }

    @Mixin(net.minecraft.client.resources.model.sprite.TextureSlots.class)
    @org.jspecify.annotations.NullMarked
    public interface DurabilityTextureSlotsAccess {
        @Accessor("resolvedValues") java.util.Map<String, net.minecraft.client.resources.model.sprite.Material> naturality$textures();
    }

    @Mixin(net.minecraft.client.renderer.item.CuboidItemModelWrapper.class)
    @org.jspecify.annotations.NullMarked
    public interface DurabilityModelFactory {
        @org.spongepowered.asm.mixin.gen.Invoker("<init>")
        static net.minecraft.client.renderer.item.CuboidItemModelWrapper naturality$newModel(
                List<net.minecraft.client.color.item.ItemTintSource> tints,
                net.minecraft.client.resources.model.geometry.QuadCollection quads,
                net.minecraft.client.renderer.item.ModelRenderProperties properties, org.joml.Matrix4fc transformation) {
            throw new AssertionError("Mixin constructor invoker was not applied");
        }
    }
}