package naturality.client.mixin;

import naturality.client.menu.MenuPreview;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CreateWorldScreen.class)
public abstract class MenuCreateWorldMixin {
    @SuppressWarnings("null") @Shadow private WorldCreationUiState uiState;
    @Unique private boolean naturality$listening;
    @Inject(method = "init", at = @At("TAIL"))
    private void naturality$seedPreview(CallbackInfo ci) {
        var screen = (CreateWorldScreen)(Object)this;
        if (!naturality$listening) {
            naturality$listening = true;
            uiState.addListener(state -> MenuPreview.requestSeed(screen, state));
        }
        MenuPreview.requestSeed(screen, uiState);
    }
}
