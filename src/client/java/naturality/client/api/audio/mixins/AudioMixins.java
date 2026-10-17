package naturality.client.api.audio.mixins;

import com.mojang.blaze3d.audio.Channel;
import com.llamalad7.mixinextras.sugar.Local;
import naturality.client.api.audio.WeatherAudioFilter;
import naturality.client.audio.underwater.UnderwaterAudio;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.ALC;
import org.lwjgl.openal.EXTEfx;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.Map;
import java.util.function.Consumer;
import naturality.Naturality;
import naturality.client.systems.weather.framework.WeatherClient.WeatherSoundEnvironment;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;

/** Feature integration hooks; each nested type retains its independent Mixin target and gate. */
public final class AudioMixins {
    private AudioMixins() {}

    @Mixin(Channel.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class WeatherAudioChannelMixin implements WeatherAudioFilter {
        @Shadow @Final private int source;
        @Unique private int naturality$weatherFilter;
        @Unique private float naturality$indoor;
        @Unique private float naturality$underwater;
        @Unique private boolean naturality$uiSound;

        @Override public void naturality$setUiSound(boolean uiSound) {
            naturality$uiSound = uiSound;
        }

        @Override public void naturality$setEnvironmentAmounts(float indoor, float underwater) {
            naturality$indoor = Math.clamp(indoor, 0, 1);
            naturality$underwater = naturality$uiSound ? 0 : Math.clamp(underwater, 0, 1);
            float sleep = naturality.client.systems.sleep.SleepVisuals.SleepAudio.amount();
            if (!ALC.getCapabilities().ALC_EXT_EFX) return;
            if (naturality$indoor <= .001F && naturality$underwater <= .001F && sleep <= .001F) {
                if (naturality$weatherFilter != 0)
                    AL10.alSourcei(source, EXTEfx.AL_DIRECT_FILTER, EXTEfx.AL_FILTER_NULL);
                return;
            }
            if (naturality$weatherFilter == 0) {
                naturality$weatherFilter = EXTEfx.alGenFilters();
                EXTEfx.alFilteri(naturality$weatherFilter, EXTEfx.AL_FILTER_TYPE, EXTEfx.AL_FILTER_LOWPASS);
            }
            EXTEfx.alFilterf(naturality$weatherFilter, EXTEfx.AL_LOWPASS_GAIN,
                (1 - .25F * naturality$indoor) * naturality.client.systems.sleep.SleepVisuals.SleepAudio.gain(sleep));
            EXTEfx.alFilterf(naturality$weatherFilter, EXTEfx.AL_LOWPASS_GAINHF,
                (1 - .82F * naturality$indoor) * (float) Math.pow(.015, naturality$underwater) * naturality.client.systems.sleep.SleepVisuals.SleepAudio.highFrequencyGain(sleep));
            AL10.alSourcei(source, EXTEfx.AL_DIRECT_FILTER, naturality$weatherFilter);
        }

        @Inject(method = "play", at = @At("HEAD"))
        private void naturality$filterBeforePlayback(CallbackInfo ci) {
            // Short one-shots must be filtered before their first audible sample.
            naturality$setEnvironmentAmounts(naturality$indoor, UnderwaterAudio.amount());
        }

        @Inject(method = "destroy", at = @At("HEAD"))
        private void naturality$releaseWeatherFilter(CallbackInfo ci) {
            if (naturality$weatherFilter == 0) return;
            AL10.alSourcei(source, EXTEfx.AL_DIRECT_FILTER, EXTEfx.AL_FILTER_NULL);
            EXTEfx.alDeleteFilters(naturality$weatherFilter);
            naturality$weatherFilter = 0;
        }
    }

    @Mixin(SoundEngine.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class WeatherAudioEngineMixin {
        private static final Identifier naturality$rain = Identifier.withDefaultNamespace("weather.rain");
        private static final Identifier naturality$rainAbove = Identifier.withDefaultNamespace("weather.rain.above");
        private static final Identifier naturality$weakWind = Naturality.id("wind_weak");
        private static final Identifier naturality$strongWind = Naturality.id("wind_strong");
        @SuppressWarnings("null") @Shadow @Final private Map<SoundInstance, ChannelAccess.ChannelHandle> instanceToChannel;

        @ModifyArg(method = "play", at = @At(value = "INVOKE",
                target = "Lnet/minecraft/client/sounds/ChannelAccess$ChannelHandle;execute(Ljava/util/function/Consumer;)V",
                ordinal = 0), index = 0)
        private Consumer<Channel> naturality$classifySound(Consumer<Channel> setup, @Local(argsOnly = true) SoundInstance sound) {
            boolean uiSound = sound.getSource() == SoundSource.UI;
            return channel -> {
                // Classify on the sound thread before playback, including short menu clicks.
                ((WeatherAudioFilter) channel).naturality$setUiSound(uiSound);
                setup.accept(channel);
            };
        }

        @Inject(method = "tick", at = @At("TAIL"))
        private void naturality$filterWeather(boolean paused, CallbackInfo ci) {
            if (paused) return;
            float indoor = naturality.config.startup.FeatureConfig.Feature.WEATHER_AUDIO.enabled() ? WeatherSoundEnvironment.indoor() : 0;
            float underwater = UnderwaterAudio.amount();
            instanceToChannel.forEach((sound, handle) -> {
                Identifier id = sound.getIdentifier();
                boolean weather = id.equals(naturality$rain) || id.equals(naturality$rainAbove)
                        || id.equals(naturality$weakWind) || id.equals(naturality$strongWind);
                handle.execute(channel -> ((WeatherAudioFilter) channel)
                    .naturality$setEnvironmentAmounts(weather ? indoor : 0, underwater));
            });
        }

        @Inject(method = "tick", at = @At("HEAD"))
        private void naturality$listenerImmersion(boolean paused, CallbackInfo ci) {
            UnderwaterAudio.update();
            naturality.client.systems.sleep.SleepVisuals.SleepAudio.update();
        }
    }
}
