package naturality.client.mixin;

import naturality.client.portal.PortalClippedCollector;
import naturality.client.portal.PortalClippedSubmit;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.feature.ItemFeatureRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({ModelFeatureRenderer.Submit.class,ItemFeatureRenderer.Submit.class})
public abstract class PortalFeatureSubmitMixin implements PortalClippedSubmit {
    @Unique private PortalClippedCollector.Plane naturality$clipPlane;
    @Override public PortalClippedCollector.Plane naturality$clipPlane() { return naturality$clipPlane; }
    @Override public void naturality$clipPlane(PortalClippedCollector.Plane plane) { naturality$clipPlane=plane; }
    @Inject(method="<init>",at=@At("RETURN"))
    private void naturality$plane(CallbackInfo ci) {PortalClippedCollector.remember(this);}
}
