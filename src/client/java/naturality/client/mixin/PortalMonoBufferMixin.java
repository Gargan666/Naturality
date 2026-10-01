package naturality.client.mixin;

import com.mojang.blaze3d.audio.SoundBuffer;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import javax.sound.sampled.AudioFormat;
import net.minecraft.client.sounds.JOrbisAudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceProvider;
import net.minecraft.util.Util;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SoundBufferLibrary.class)
public abstract class PortalMonoBufferMixin {
    @SuppressWarnings("null") @Shadow @Final private ResourceProvider resourceManager;
    @SuppressWarnings("null") @Shadow @Final private Map<Identifier, CompletableFuture<SoundBuffer>> cache;

    @Inject(method = "getCompleteBuffer", at = @At("HEAD"), cancellable = true)
    private void naturality$mono(Identifier location, CallbackInfoReturnable<CompletableFuture<SoundBuffer>> cir) {
        String prefix = "positional_sound/";
        if (!location.getNamespace().equals("naturality") || !location.getPath().startsWith(prefix)) return;
        String source = location.getPath().substring(prefix.length());
        int separator = source.indexOf('/');
        Identifier original = Identifier.fromNamespaceAndPath(source.substring(0, separator), source.substring(separator + 1));
        // Use the existing cache so resource reloads also release these OpenAL buffers.
        cir.setReturnValue(cache.computeIfAbsent(location, _ -> CompletableFuture.supplyAsync(() -> {
            try (var input = resourceManager.open(original); var stream = new JOrbisAudioStream(input)) {
                AudioFormat format = stream.getFormat();
                ByteBuffer data = stream.readAll();
                int channels = format.getChannels();
                if (channels == 1) return new SoundBuffer(data, format);
                if (format.getSampleSizeInBits() != 16) throw new IOException("Expected 16-bit decoded Vorbis audio");
                data.order(format.isBigEndian() ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN);
                ByteBuffer mono = ByteBuffer.allocateDirect(data.remaining() / channels).order(data.order());
                while (data.remaining() >= channels * Short.BYTES) {
                    int sum = 0;
                    for (int channel = 0; channel < channels; channel++) sum += data.getShort();
                    mono.putShort((short) (sum / channels));
                }
                mono.flip();
                return new SoundBuffer(mono, new AudioFormat(format.getSampleRate(), 16, 1, true, format.isBigEndian()));
            } catch (IOException e) {
                throw new CompletionException(e);
            }
        }, Util.nonCriticalIoPool())));
    }
}
