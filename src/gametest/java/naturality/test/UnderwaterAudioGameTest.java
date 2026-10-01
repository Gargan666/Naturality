package naturality.test;

import com.mojang.blaze3d.audio.Channel;
import naturality.client.sound.UnderwaterAudio;
import naturality.client.sound.WeatherAudioFilter;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.ALC;
import org.lwjgl.openal.EXTEfx;

final class UnderwaterAudioGameTest {
    private static void near(float actual, float expected) {
        if (Math.abs(actual - expected) > .0001F)
            throw new AssertionError("Audio value " + actual + ", expected " + expected);
    }
    static void run(ClientGameTestContext context) {
        near(UnderwaterAudio.strength(0), 0);
        near(UnderwaterAudio.strength(1), 0);
        near(UnderwaterAudio.strength(2.5F), .5F);
        near(UnderwaterAudio.strength(4), 1);
        near(UnderwaterAudio.strength(40), 1);
        context.runOnClient(client -> {
            Channel channel;
            try {
                var create = Channel.class.getDeclaredMethod("create");
                create.setAccessible(true);
                channel = (Channel) create.invoke(null);
            } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
            if (channel == null) throw new AssertionError("OpenAL source allocation failed");
            int filter = 0;
            try {
                var controls = (WeatherAudioFilter) channel;
                channel.setVolume(.8F);
                controls.naturality$setEnvironmentAmounts(0, 1);
                if (ALC.getCapabilities().ALC_EXT_EFX) {
                    var field = Channel.class.getDeclaredField("naturality$weatherFilter");
                    field.setAccessible(true);
                    filter = field.getInt(channel);
                    if (filter == 0) throw new AssertionError("Underwater filter not allocated");
                    near(EXTEfx.alGetFilterf(filter, EXTEfx.AL_LOWPASS_GAIN), 1.0F);
                    near(EXTEfx.alGetFilterf(filter, EXTEfx.AL_LOWPASS_GAINHF), .015F);
                    controls.naturality$setEnvironmentAmounts(1, 1);
                    near(EXTEfx.alGetFilterf(filter, EXTEfx.AL_LOWPASS_GAIN), .75F);
                    near(EXTEfx.alGetFilterf(filter, EXTEfx.AL_LOWPASS_GAINHF), .18F * .015F);
                    controls.naturality$setEnvironmentAmounts(1, 0);
                    near(EXTEfx.alGetFilterf(filter, EXTEfx.AL_LOWPASS_GAIN), .75F);
                    near(EXTEfx.alGetFilterf(filter, EXTEfx.AL_LOWPASS_GAINHF), .18F);
                }
                controls.naturality$setEnvironmentAmounts(0, 0);
                var sourceField = Channel.class.getDeclaredField("source");
                sourceField.setAccessible(true);
                near(AL10.alGetSourcef(sourceField.getInt(channel), AL10.AL_GAIN), .8F);
                if (AL10.alGetError() != AL10.AL_NO_ERROR)
                    throw new AssertionError("OpenAL error updating immersion filter");
            } catch (ReflectiveOperationException e) {
                throw new AssertionError(e);
            } finally {
                channel.destroy();
            }
            if (filter != 0 && EXTEfx.alIsFilter(filter))
                throw new AssertionError("Channel destruction leaked the underwater filter");
        });
    }
}
