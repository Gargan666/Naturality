package naturality.client.mixin;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.WaterDropParticle;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WaterDropParticle.class)
public abstract class WaterDropImpactMixin extends SingleQuadParticle implements naturality.client.particle.ImpactDroplet {
    @Unique private boolean naturality$ring=true;
    @Override public void naturality$impactRing(boolean enabled){naturality$ring=enabled;}
    @Unique private boolean naturality$hit;
    @Unique private boolean naturality$untilLanding;
    @Unique private int naturality$rainDuration;
    @Unique private int naturality$rainAge;
    @Override public void naturality$rainSplash() {
        if (!naturality.config.NaturalityConfig.get().effects.rainSplashFade) return;
        var pos=net.minecraft.core.BlockPos.containing(x,y-0.16,z);
        var block=level.getBlockState(pos);
        if(!block.getFluidState().isEmpty()) return;
        double top=block.getCollisionShape(level,pos).max(net.minecraft.core.Direction.Axis.Y,
            x-pos.getX(),z-pos.getZ())+pos.getY();
        if(!Double.isFinite(top) || Math.abs(y-top)>0.15) return;
        // Predict the vanilla ballistic return, ending before its collision tick.
        double height=y, velocity=yd;
        int ticks=0;
        do {
            velocity-=gravity;
            height+=velocity;
            velocity*=0.98;
            ticks++;
        } while(height>top && ticks<80);
        naturality$rainDuration=Math.max(1,ticks-1);
        lifetime=Math.max(lifetime,naturality$rainDuration+1);
    }
    @Override public void naturality$waterDroplet(){}
    @Override public void naturality$landBeforeExpiring(){naturality$untilLanding=true;}
    @Inject(method="tick",at=@At("HEAD"),cancellable=true)
    private void naturality$keepAirborne(CallbackInfo ci) {
        if (!naturality.config.NaturalityConfig.get().effects.rainSplashFade && naturality$rainDuration>0) { naturality$rainDuration=0; alpha=1; }
        if(naturality$rainDuration>0) {
            float progress=++naturality$rainAge/(float)naturality$rainDuration;
            float fade=Math.clamp((progress-0.25F)/0.75F,0,1);
            alpha=1-fade*fade*(3-2*fade);
            if(naturality$rainAge>=naturality$rainDuration) {remove();ci.cancel();return;}
        }
        if(naturality$untilLanding && !onGround) lifetime=Math.max(lifetime,2);
    }
    @Inject(method="getLayer",at=@At("HEAD"),cancellable=true)
    private void naturality$fadeLayer(org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Layer> cir) {
        if(naturality$rainDuration>0) cir.setReturnValue(Layer.TRANSLUCENT);
    }
    protected WaterDropImpactMixin(ClientLevel level,double x,double y,double z,TextureAtlasSprite sprite) {
        super(level,x,y,z,sprite);
    }
    @Inject(method="tick",at=@At("TAIL"))
    private void naturality$impact(CallbackInfo ci) {
        if(!naturality$hit && naturality.client.fluid.LiquidDropletContact.impact(level,false,xo,yo,zo,x,y,z,naturality$ring)) {
            naturality$hit=true;
            remove();
        }
    }
}






