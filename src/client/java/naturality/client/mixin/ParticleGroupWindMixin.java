package naturality.client.mixin;

import naturality.client.particle.WindParticleControl;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleGroup;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ParticleGroup.class)
public abstract class ParticleGroupWindMixin {
    @Inject(method="tickParticle",at=@At("HEAD"),cancellable=true)
    private void naturality$removeHidden(Particle particle,CallbackInfo ci) {
        if(particle.isAlive() && ((naturality.client.particle.ParticleVisibility.Access)particle).naturality$hidden(false)) {
            particle.remove();
            naturality.client.particle.ParticleVisibility.removed();
            ci.cancel();
        }
    }
    @Inject(method = "tickParticle", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/particle/Particle;tick()V", shift = At.Shift.AFTER))
    private void naturality$applyWind(Particle particle, CallbackInfo ci) {
        ((WindParticleControl.Access)particle).naturality$applyWind();
    }
}
