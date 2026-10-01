package naturality.client.mixin;

import naturality.client.snow.SnowSectionVisibility;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public abstract class SnowLevelRendererResetMixin {
    @Inject(method="resetLevelRenderData",at=@At("HEAD"))
    private void naturality$clearSnowBounds(CallbackInfo ci) { SnowSectionVisibility.clear(); }
}
