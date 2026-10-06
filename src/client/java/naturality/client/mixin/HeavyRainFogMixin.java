package naturality.client.mixin;

import naturality.weather.WeatherSystem;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.world.level.material.FogType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FogRenderer.class)
public abstract class HeavyRainFogMixin {
    @Inject(method = "setupFog", at = @At("RETURN"))
    private void naturality$heavyRainFog(Camera camera, int distance, DeltaTracker delta,
            float darken, ClientLevel level, CallbackInfoReturnable<FogData> cir) {
        var weather = WeatherSystem.renderState(level);
        if (weather == null || camera.getFluidInCamera() != FogType.NONE) return;
        float strength = weather.heavyRainFog();
        if (strength <= 0) return;

        // This outdoor exposure is eased by the client tick, so entering or
        // leaving shelter cannot snap the fog band. Indoors, postpone its far
        // boundary to keep nearby room details legible while retaining haze.
        float exposure = 1 - naturality.client.weather.WeatherSoundEnvironment.indoor();
        FogData fog = cir.getReturnValue();
        naturality.client.weather.HeavyRainFog.apply(fog, weather, exposure, distance);
    }
}
