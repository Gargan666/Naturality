package naturality.client.mixin;

import naturality.client.snow.SnowTextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlas;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TextureAtlas.class)
public abstract class SnowAtlasTickMixin {
    @Inject(method="cycleAnimationFrames",at=@At("TAIL"))
    private void naturality$compositeAnimation(CallbackInfo ci) {
        SnowTextureAtlas.tick((TextureAtlas)(Object)this);
    }
}
