package naturality.client.mixin;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.DripParticle;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DripParticle.class)
public abstract class WaterDripImpactMixin extends SingleQuadParticle {
    @SuppressWarnings("null") @Shadow @Final private Fluid type;
    @SuppressWarnings("null") // The target constructor initializes this shadow field.
    protected WaterDripImpactMixin(ClientLevel level,double x,double y,double z,TextureAtlasSprite sprite) {
        super(level,x,y,z,sprite);
    }
    @Inject(method="tick",at=@At(value="INVOKE",target="Lnet/minecraft/client/particle/DripParticle;postMoveUpdate()V"),cancellable=true)
    private void naturality$impact(CallbackInfo ci) {
        if(gravity>0.01F && (type==Fluids.WATER || type==Fluids.LAVA) && naturality.client.fluid.LiquidDropletContact.impact(level,type==Fluids.LAVA,xo,yo,zo,x,y,z)) {
            remove();
            ci.cancel();
        }
    }
}


