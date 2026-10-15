package naturality.client.mixin;

import naturality.client.menu.MenuPreview;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.sounds.SoundSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SoundManager.class)
public abstract class MenuPreviewSoundMixin {
    private static boolean worldSound(SoundInstance sound) {
        return MenuPreview.active() && sound.getSource() != SoundSource.MASTER
                && sound.getSource() != SoundSource.MUSIC && sound.getSource() != SoundSource.UI;
    }
    @Inject(method = "play", at = @At("HEAD"), cancellable = true)
    private void naturality$quietWorld(SoundInstance sound, CallbackInfoReturnable<SoundEngine.PlayResult> cir) {
        if (worldSound(sound)) cir.setReturnValue(SoundEngine.PlayResult.NOT_STARTED);
    }
    @Inject(method = "playDelayed", at = @At("HEAD"), cancellable = true)
    private void naturality$quietDelayed(SoundInstance sound, int delay, CallbackInfo ci) { if (worldSound(sound)) ci.cancel(); }
    @Inject(method = "queueTickingSound", at = @At("HEAD"), cancellable = true)
    private void naturality$quietLoop(net.minecraft.client.resources.sounds.TickableSoundInstance sound, CallbackInfo ci) {
        if (worldSound(sound)) ci.cancel();
    }
}
