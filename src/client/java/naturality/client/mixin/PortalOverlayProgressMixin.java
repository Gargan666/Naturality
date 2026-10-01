package naturality.client.mixin;
import naturality.portal.PortalCrossing;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(LocalPlayer.class)
public abstract class PortalOverlayProgressMixin {
    @Inject(method="handlePortalTransitionEffect",at=@At("TAIL"))
    private void naturality$progress(boolean active,CallbackInfo ci){
        if (!naturality.config.GameplaySettings.clientPhysicalPortalEntry()) return;
        var player=(LocalPlayer)(Object)this;
        var crossing=PortalCrossing.get(player);
        float previous=player.oPortalEffectIntensity;
        double distance=crossing==null?0:crossing.signed(player.getEyePosition().x,player.getEyePosition().z);
        double half=player.getBbWidth()/2.0;
        float target=crossing!=null && crossing.valid(player)
            ? Math.max(0.0125F,(float)Math.clamp((half-distance)/Math.max(0.001,half-0.1),0,1)):0;
        player.portalEffectIntensity=target>0?target:Math.max(0,previous-0.1F);
        if(player.portalEffectIntensity<0.00001F) player.portalEffectIntensity=0;
    }
}
