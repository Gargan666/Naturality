package naturality.client.mixin;

import naturality.client.fluid.WaterVisuals;
import naturality.config.NaturalityConfig;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.environment.WaterFogEnvironment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WaterFogEnvironment.class)
public abstract class WaterFogMixin {
    @Inject(method = "setupFog", at = @At("TAIL"))
    private void naturality$directionalFog(FogData fog, Camera camera, ClientLevel level,
            float distance, DeltaTracker delta, CallbackInfo ci) {
        if (!WaterVisuals.ready() || !NaturalityConfig.get().liquids.waterDepth) return;
        // The composite supplies water extinction. Keep distance culling, but
        // remove the isotropic water wall that would obscure the upward window.
        fog.environmentalStart = 1.0e6F;
        fog.environmentalEnd = 1.0e6F;
        fog.skyEnd = distance;
        fog.cloudEnd = distance;
    }
}
