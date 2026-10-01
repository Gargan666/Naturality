package naturality.client.mixin;

import naturality.client.weather.ParticleWeather;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.WeatherEffectRenderer;
import net.minecraft.client.renderer.state.level.WeatherRenderState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WeatherEffectRenderer.class)
public abstract class WeatherRendererMixin {
    @Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
    private void naturality$particles(ClientLevel level, float partial, Vec3 camera, WeatherRenderState render, CallbackInfo ci) {
        if (!ParticleWeather.enabled(level)) return;
        render.reset();
        ci.cancel();
    }
}
