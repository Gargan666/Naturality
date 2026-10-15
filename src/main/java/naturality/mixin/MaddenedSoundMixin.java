package naturality.mixin;

import naturality.entity.MaddenedMob;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(Entity.class)
public abstract class MaddenedSoundMixin {
    @ModifyVariable(method = "playSound(Lnet/minecraft/sounds/SoundEvent;FF)V", at = @At("HEAD"), argsOnly = true, ordinal = 1)
    private float naturality$slow(float pitch, net.minecraft.sounds.SoundEvent sound, float volume, float originalPitch) {
        if (MaddenedMob.isExplosionSound(sound.location())) return pitch;
        return (Object)this instanceof MaddenedMob maddened ? maddened.maddenedSoundPitch(pitch) : pitch;
    }
}
