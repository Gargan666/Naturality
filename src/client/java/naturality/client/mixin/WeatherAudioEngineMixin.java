package naturality.client.mixin;

import java.util.Map;
import naturality.Naturality;
import naturality.client.sound.WeatherAudioFilter;
import naturality.client.weather.WeatherSoundEnvironment;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SoundEngine.class)
public abstract class WeatherAudioEngineMixin {
    private static final Identifier naturality$rain = Identifier.withDefaultNamespace("weather.rain");
    private static final Identifier naturality$rainAbove = Identifier.withDefaultNamespace("weather.rain.above");
    private static final Identifier naturality$weakWind = Naturality.id("wind_weak");
    private static final Identifier naturality$strongWind = Naturality.id("wind_strong");
    @SuppressWarnings("null") @Shadow @Final private Map<SoundInstance, ChannelAccess.ChannelHandle> instanceToChannel;

    @Inject(method = "tick", at = @At("TAIL"))
    private void naturality$filterWeather(boolean paused, CallbackInfo ci) {
        if (paused) return;
        float indoor = WeatherSoundEnvironment.indoor();
        instanceToChannel.forEach((sound, handle) -> {
            Identifier id = sound.getIdentifier();
            if (id.equals(naturality$rain) || id.equals(naturality$rainAbove)
                    || id.equals(naturality$weakWind) || id.equals(naturality$strongWind))
                handle.execute(channel -> ((WeatherAudioFilter) channel).naturality$setIndoorAmount(indoor));
        });
    }
}
