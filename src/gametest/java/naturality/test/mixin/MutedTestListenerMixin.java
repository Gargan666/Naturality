package naturality.test.mixin;

import com.mojang.blaze3d.audio.Listener;
import org.lwjgl.openal.AL10;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Mute device output, preserving sound lifecycles and volumes for audio tests. Test JAR only. */
@Mixin(Listener.class)
public abstract class MutedTestListenerMixin {
    @Inject(method = "reset", at = @At("TAIL"))
    private void naturality$muteTestOutput(CallbackInfo ci) {
        // IDE launches may include this source set even when no tests are running.
        if (Boolean.getBoolean("naturality.test.muteAudio")) {
            AL10.alListenerf(AL10.AL_GAIN, 0.0F);
        }
    }
}
