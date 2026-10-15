package naturality.client.mixin;

import naturality.client.menu.MenuPreview;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LocalPlayer.class)
public abstract class MenuPreviewPlayerMixin {
    @Inject(method = "aiStep", at = @At("HEAD"), cancellable = true)
    private void naturality$inertPreview(CallbackInfo ci) { if (MenuPreview.active()) ci.cancel(); }
}
