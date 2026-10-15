package naturality.client.ui.menus.title.mixins;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.LogoRenderer;
import net.minecraft.client.gui.components.SplashRenderer;
import net.minecraft.client.gui.screens.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(TitleScreen.class)
@org.jspecify.annotations.NullMarked
public abstract class TitleLogoMixin {
    @Redirect(method = "extractRenderState", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/components/LogoRenderer;extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IF)V"))
    private void naturality$titleLogo(LogoRenderer renderer, GuiGraphicsExtractor graphics, int width, float alpha) {
        naturality.client.ui.menus.title.TitleLogo.logo(renderer, graphics, width, alpha);
    }

    @Redirect(method = "extractRenderState", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/components/SplashRenderer;extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;ILnet/minecraft/client/gui/Font;F)V"))
    private void naturality$titleSplash(SplashRenderer splash, GuiGraphicsExtractor graphics,
                                       int width, Font font, float alpha) {
        naturality.client.ui.menus.title.TitleLogo.splash(splash, graphics, width, font, alpha);
    }
}
