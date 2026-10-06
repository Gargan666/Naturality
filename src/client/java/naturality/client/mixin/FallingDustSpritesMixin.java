package naturality.client.mixin;

import naturality.client.particle.SandParticleSprites;
import net.minecraft.client.particle.FallingDustParticle;
import net.minecraft.client.particle.SpriteSet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FallingDustParticle.Provider.class)
public class FallingDustSpritesMixin {
    @Inject(method = "<init>", at = @At("RETURN"))
    private void naturality$captureSprites(SpriteSet sprites, CallbackInfo ci) {
        SandParticleSprites.sprites = sprites;
    }
}
