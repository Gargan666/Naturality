package naturality.client.mixin;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.DeltaTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(Hud.class)
public abstract class LightningFlashOverlayMixin {
    @Inject(method="extractRenderState",at=@At("HEAD"))
    private void naturality$flash(GuiGraphicsExtractor graphics,DeltaTracker delta,CallbackInfo ci) {
        naturality.client.weather.LightningFlashes.extract(graphics);
    }
}