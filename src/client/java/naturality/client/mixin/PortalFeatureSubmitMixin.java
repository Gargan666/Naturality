package naturality.client.mixin;

import naturality.client.portal.PortalClippedCollector;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.feature.ItemFeatureRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({ModelFeatureRenderer.Submit.class,ItemFeatureRenderer.Submit.class})
public abstract class PortalFeatureSubmitMixin {
    @Inject(method="<init>",at=@At("RETURN"))
    private void naturality$plane(CallbackInfo ci) {PortalClippedCollector.remember(this);}
}
