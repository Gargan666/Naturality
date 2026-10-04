package naturality.client.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import naturality.client.weather.WeatherParticleContext;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(ParticleEngine.class)
public abstract class WeatherParticleEngineMixin {
    // Initialized by ParticleEngine; mixin shadow fields have no constructor assignment.
    @SuppressWarnings("null") @Shadow protected ClientLevel level;

    @WrapMethod(method = "tick")
    private void naturality$shareWeatherQueries(Operation<Void> original) {
        WeatherParticleContext.begin(level);
        try { original.call(); }
        finally { WeatherParticleContext.end(); }
    }
}
