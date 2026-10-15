package naturality.test.mixin;
import naturality.test.MaddenedSoundProbe;
import net.minecraft.world.entity.Entity;
import net.minecraft.sounds.SoundEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(Entity.class)
public abstract class MaddenedSoundProbeMixin {
    @Inject(method="playSound(Lnet/minecraft/sounds/SoundEvent;FF)V", at=@At("TAIL"))
    private void probe(SoundEvent sound,float volume,float pitch,CallbackInfo ci) { MaddenedSoundProbe.pitch=pitch; }
}
