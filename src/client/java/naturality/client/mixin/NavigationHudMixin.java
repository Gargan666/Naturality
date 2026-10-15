package naturality.client.mixin;

import naturality.client.navigation.NavigationClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Hud.class)
public abstract class NavigationHudMixin {
    @Shadow private int overlayMessageTime;
    @Inject(method = "extractOverlayMessage", at = @At("HEAD"))
    private void naturality$readout(GuiGraphicsExtractor graphics, DeltaTracker delta, CallbackInfo ci) {
        if (overlayMessageTime > 0) return;
        var message = NavigationClient.message();
        if (message == null) return;
        var font = Minecraft.getInstance().font;
        int width = font.width(message);
        graphics.nextStratum();
        graphics.pose().pushMatrix();
        graphics.pose().translate(graphics.guiWidth() / 2, graphics.guiHeight() - 68);
        graphics.textWithBackdrop(font, message, -width / 2, -4, width, 0xFFFFFFFF);
        graphics.pose().popMatrix();
    }
}
