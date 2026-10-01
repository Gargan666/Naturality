package naturality.client.mixin;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.LavaParticle;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LavaParticle.class)
public abstract class LavaPopImpactMixin extends SingleQuadParticle {
    protected LavaPopImpactMixin(ClientLevel level,double x,double y,double z,TextureAtlasSprite sprite){super(level,x,y,z,sprite);}
    @Inject(method="tick",at=@At("TAIL"))
    private void naturality$land(CallbackInfo ci) {
        if(naturality.client.fluid.LiquidDropletContact.impact(level,true,xo,yo,zo,x,y,z))remove();
    }
}

