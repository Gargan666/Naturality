package naturality.client.mixin;

import naturality.client.lighting.DynamicLighting;
import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientLevel.class)
public abstract class DynamicLightGeometryMixin {
    @Inject(method = "setBlocksDirty", at = @At("HEAD"))
    private void naturality$refreshOcclusion(CallbackInfo ci) { DynamicLighting.invalidate(); }
}
