package naturality.client.mixin;

import naturality.client.sleep.SleepVisuals;
import net.minecraft.client.particle.Particle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Scale movement requests, never persistent velocity, age or emitter ticking. */
@Mixin(Particle.class)
public class SleepParticleVelocityMixin {
    @ModifyVariable(method = "move", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private double naturality$speedX(double velocity) { return velocity * SleepVisuals.speed(); }
    @ModifyVariable(method = "move", at = @At("HEAD"), argsOnly = true, ordinal = 1)
    private double naturality$speedY(double velocity) { return velocity * SleepVisuals.speed(); }
    @ModifyVariable(method = "move", at = @At("HEAD"), argsOnly = true, ordinal = 2)
    private double naturality$speedZ(double velocity) { return velocity * SleepVisuals.speed(); }
}
