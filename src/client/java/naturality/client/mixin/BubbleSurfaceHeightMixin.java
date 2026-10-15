package naturality.client.mixin;

import naturality.client.fluid.WaterSurface;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.BubbleParticle;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A water-tagged block can still be air above its sloped surface. */
@Mixin(BubbleParticle.class)
public abstract class BubbleSurfaceHeightMixin extends SingleQuadParticle {
    protected BubbleSurfaceHeightMixin(ClientLevel level,double x,double y,double z,TextureAtlasSprite sprite){super(level,x,y,z,sprite);}
    @Inject(method="tick",at=@At("TAIL"))
    private void naturality$leaveSurface(CallbackInfo ci) {
        if(!naturality.config.NaturalityConfig.get().liquids.water)return;
        var surface=WaterSurface.at(level,BlockPos.containing(x,y,z));
        if(surface!=null && y>=surface.height(x-surface.pos().getX(),z-surface.pos().getZ()))remove();
    }
}
