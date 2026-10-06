package naturality.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import naturality.client.weather.ThunderAudio;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LightningBolt.class)
public abstract class LightningAudioMixin {
    @WrapOperation(method = "tick", at = @At(value = "INVOKE", target =
        "Lnet/minecraft/world/level/Level;playLocalSound(DDDLnet/minecraft/sounds/SoundEvent;Lnet/minecraft/sounds/SoundSource;FFZ)V"))
    private void naturality$thunder(Level level, double x, double y, double z, SoundEvent sound,
            SoundSource source, float volume, float pitch, boolean delay, Operation<Void> original) {
        if (level instanceof ClientLevel client)
            ThunderAudio.schedule(client, new Vec3(x,y,z), sound, source, volume, pitch);
        else original.call(level,x,y,z,sound,source,volume,pitch,delay);
    }
    @WrapOperation(method = "tick", at = @At(value = "INVOKE", target =
        "Lnet/minecraft/world/level/Level;setSkyFlashTime(I)V"))
    private void naturality$surfaceFlash(Level level, int ticks, Operation<Void> original) {
        if (ThunderAudio.audible()) original.call(level, ticks);
    }
}
