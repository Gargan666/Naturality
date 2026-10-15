package naturality.client.mixin;

import naturality.client.menu.MenuWorld;
import naturality.client.menu.MenuPreview;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.renderer.Panorama;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Panorama.class)
public abstract class MenuPanoramaMixin {
    @Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
    private void naturality$world(GuiGraphicsExtractor graphics, int width, int height, CallbackInfo ci) {
        var client = Minecraft.getInstance();
        if (!(client.gui.screen() instanceof TitleScreen
                || client.gui.screen() instanceof net.minecraft.client.gui.screens.worldselection.SelectWorldScreen
                || client.gui.screen() instanceof net.minecraft.client.gui.screens.worldselection.CreateWorldScreen)) return;
        if (!MenuPreview.active()) MenuWorld.camera().frame(client.options.panoramaSpeed().get());
        ci.cancel();
    }
}
