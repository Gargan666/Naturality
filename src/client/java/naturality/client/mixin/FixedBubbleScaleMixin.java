package naturality.client.mixin;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.BubbleColumnUpParticle;
import net.minecraft.client.particle.BubbleParticle;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.WaterCurrentDownParticle;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keep individual texture pixels the same world-space size for bubbles from all vanilla sources. */
@Mixin({BubbleParticle.class, BubbleColumnUpParticle.class, WaterCurrentDownParticle.class})
public abstract class FixedBubbleScaleMixin extends SingleQuadParticle {
    private static final float NATURALITY_BUBBLE_PIXEL_SIZE = 0.1F / 7.0F;

    protected FixedBubbleScaleMixin(ClientLevel level, double x, double y, double z,
                                    TextureAtlasSprite sprite) {
        super(level, x, y, z, sprite);
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void naturality$fixBubbleScale(CallbackInfo ci) {
        if (!naturality.config.NaturalityConfig.get().effects.fixedBubbleScale) return;
        var sprite = ((ParticleSpriteAccess)this).naturality$sprite();
        // The vanilla bubble is 7x7, so keep its existing pixel scale and size
        // every variant's quad from that sprite's actual pixel dimensions.
        quadSize = NATURALITY_BUBBLE_PIXEL_SIZE
            * Math.max(sprite.contents().width(), sprite.contents().height());
    }
}
