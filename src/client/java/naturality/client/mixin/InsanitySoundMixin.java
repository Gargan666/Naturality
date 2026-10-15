package naturality.client.mixin;

import naturality.client.insanity.Insanity;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(SoundManager.class)
public abstract class InsanitySoundMixin {
    @ModifyVariable(method = "play", at = @At("HEAD"), argsOnly = true)
    private SoundInstance naturality$voice(SoundInstance sound) { return Insanity.sound(sound); }
}
