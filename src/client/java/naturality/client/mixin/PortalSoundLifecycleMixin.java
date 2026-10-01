package naturality.client.mixin;

import naturality.client.sound.PortalSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(SoundManager.class)
public abstract class PortalSoundLifecycleMixin {
    @ModifyArg(method = "play", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/sounds/SoundEngine;play(Lnet/minecraft/client/resources/sounds/SoundInstance;)Lnet/minecraft/client/sounds/SoundEngine$PlayResult;"), index = 0)
    private SoundInstance naturality$managedPortalSound(SoundInstance sound) {
        return PortalSoundInstance.wrap(sound);
    }
}
