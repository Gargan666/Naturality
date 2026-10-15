package naturality.client.mixin;

import naturality.client.menu.MenuPreview;
import net.minecraft.client.Minecraft;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Hud.class)
public abstract class MenuPreviewHudMixin {
    @Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
    private void naturality$hidePreviewPlayer(GuiGraphicsExtractor graphics, DeltaTracker delta, CallbackInfo ci) {
        if (!MenuPreview.active()) return;
        Minecraft.getInstance().gameRenderer.gameRenderState().guiRenderState.isHudHidden = true;
        ci.cancel();
    }
}
