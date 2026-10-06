package naturality.test.mixin;

import naturality.test.EndFootstepParticleChecks;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleOptions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientLevel.class)
public class EndFootstepProbeMixin {
    @Inject(method = "addParticle(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)V", at = @At("HEAD"))
    private void naturality$recordFootstep(ParticleOptions type, double x, double y, double z,
            double vx, double vy, double vz, CallbackInfo ci) {
        EndFootstepParticleChecks.record(type, y, vx, vy, vz);
    }
}
