package naturality.client.mixin;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(SpriteContents.class)
public abstract class OpaquePortalSpriteMixin {
    @Inject(method="<init>(Lnet/minecraft/resources/Identifier;Lnet/minecraft/client/resources/metadata/animation/FrameSize;Lcom/mojang/blaze3d/platform/NativeImage;Ljava/util/Optional;Ljava/util/List;Ljava/util/Optional;)V",at=@At("HEAD"))
    private static void naturality$opaque(Identifier name,FrameSize size,NativeImage image,java.util.Optional<?> animation,
            java.util.List<?> metadata,java.util.Optional<?> texture,CallbackInfo ci){
        if(!name.equals(Identifier.withDefaultNamespace("block/nether_portal"))) return;
        for(int y=0;y<image.getHeight();y++) for(int x=0;x<image.getWidth();x++)
            image.setPixel(x,y,image.getPixel(x,y)|0xFF000000);
    }
}
