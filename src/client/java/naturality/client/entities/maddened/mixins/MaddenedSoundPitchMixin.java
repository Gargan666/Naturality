package naturality.client.entities.maddened.mixins;

import naturality.entities.maddened.MaddenedVariants.MaddenedMob;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SoundEngine.class)
@org.jspecify.annotations.NullMarked
public abstract class MaddenedSoundPitchMixin {
    @Inject(method="calculatePitch", at=@At("RETURN"), cancellable=true)
    private void naturality$lowPitch(SoundInstance sound, CallbackInfoReturnable<Float> cir) {
        float pitch=sound.getPitch();
        if(MaddenedMob.isExplosionSound(sound.getIdentifier())) return;
        var level=Minecraft.getInstance().level;
        if(pitch>=.5F || level==null || sound.isRelative() || !sound.getIdentifier().getPath().startsWith("entity.")) return;
        for(var entity:level.entitiesForRendering()) if(entity instanceof MaddenedMob
                && entity.distanceToSqr(sound.getX(),sound.getY(),sound.getZ())<1) {
            cir.setReturnValue(Math.clamp(pitch,.05F,2F));
            return;
        }
    }
}
