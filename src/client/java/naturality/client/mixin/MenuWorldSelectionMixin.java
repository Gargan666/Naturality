package naturality.client.mixin;

import naturality.client.menu.MenuPreview;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.world.level.storage.LevelSummary;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SelectWorldScreen.class)
public abstract class MenuWorldSelectionMixin {
    @Inject(method = "updateButtonStatus", at = @At("TAIL"))
    private void naturality$selectedSpawn(LevelSummary summary, CallbackInfo ci) {
        if (summary != null && summary.primaryActionActive())
            MenuPreview.requestSaved((SelectWorldScreen)(Object)this, summary.getLevelId());
    }
}
