package naturality.client.mixin;

import naturality.client.menu.MenuPreview;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Options.class)
public abstract class MenuPreviewOptionsMixin {
    @Inject(method = "getEffectiveRenderDistance", at = @At("HEAD"), cancellable = true)
    private void naturality$orbitView(CallbackInfoReturnable<Integer> ci) {
        if (MenuPreview.active()) ci.setReturnValue(16);
    }
}
