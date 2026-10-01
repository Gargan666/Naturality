package naturality.client.mixin;

import com.mojang.blaze3d.audio.Channel;
import naturality.client.sound.WeatherAudioFilter;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.ALC;
import org.lwjgl.openal.EXTEfx;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Channel.class)
public abstract class WeatherAudioChannelMixin implements WeatherAudioFilter {
    @Shadow @Final private int source;
    @Unique private int naturality$weatherFilter;

    @Override public void naturality$setIndoorAmount(float indoor) {
        if (indoor <= .001F) {
            if (naturality$weatherFilter != 0)
                AL10.alSourcei(source, EXTEfx.AL_DIRECT_FILTER, EXTEfx.AL_FILTER_NULL);
            return;
        }
        // EFX is optional on OpenAL devices; volume control still works without it.
        if (!ALC.getCapabilities().ALC_EXT_EFX) return;
        if (naturality$weatherFilter == 0) {
            naturality$weatherFilter = EXTEfx.alGenFilters();
            EXTEfx.alFilteri(naturality$weatherFilter, EXTEfx.AL_FILTER_TYPE, EXTEfx.AL_FILTER_LOWPASS);
        }
        float amount = Math.clamp(indoor, 0, 1);
        EXTEfx.alFilterf(naturality$weatherFilter, EXTEfx.AL_LOWPASS_GAIN, 1 - .25F * amount);
        EXTEfx.alFilterf(naturality$weatherFilter, EXTEfx.AL_LOWPASS_GAINHF, 1 - .82F * amount);
        AL10.alSourcei(source, EXTEfx.AL_DIRECT_FILTER, naturality$weatherFilter);
    }

    @Inject(method = "destroy", at = @At("HEAD"))
    private void naturality$releaseWeatherFilter(CallbackInfo ci) {
        if (naturality$weatherFilter == 0) return;
        AL10.alSourcei(source, EXTEfx.AL_DIRECT_FILTER, EXTEfx.AL_FILTER_NULL);
        EXTEfx.alDeleteFilters(naturality$weatherFilter);
        naturality$weatherFilter = 0;
    }
}
