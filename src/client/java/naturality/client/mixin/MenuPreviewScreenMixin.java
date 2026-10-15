package naturality.client.mixin;

import naturality.client.menu.MenuPreview;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Gui.class)
public abstract class MenuPreviewScreenMixin {
    @ModifyVariable(method = "setScreen", at = @At("HEAD"), argsOnly = true)
    private Screen naturality$keepMenu(Screen screen) { return MenuPreview.loadingScreen(screen); }
    @Inject(method = "isPausing", at = @At("HEAD"), cancellable = true)
    private void naturality$streamBehindLoading(CallbackInfoReturnable<Boolean> cir) {
        if (MenuPreview.preparingOwnWorld()) cir.setReturnValue(false);
    }
}
