package naturality.client.mixin;

import naturality.client.particle.BorderSplashParticle;
import naturality.client.particle.ParticleVisibility;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.SingleQuadParticle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(Particle.class)
public abstract class ParticleVisibilityMixin implements ParticleVisibility.Access {
    @SuppressWarnings("null") @Shadow protected ClientLevel level;
    @Shadow protected double x,y,z,xo,yo,zo;

    @Override public boolean naturality$hidden(boolean spawning) {
        var self=(Particle)(Object)this;
        if(!(self instanceof SingleQuadParticle quad) || !ParticleVisibility.supported(self))return false;
        double size=Math.max(Math.abs(quad.getQuadSize(0)),Math.abs(quad.getQuadSize(1)));
        // A newborn border spray may be enlarged immediately after createParticle returns.
        if(spawning && self instanceof BorderSplashParticle)size=Math.max(size,1);
        double radius=size*Math.sqrt(2)+.001;
        return ParticleVisibility.hidden(level,Math.min(x,xo)-radius,Math.min(y,yo)-radius,Math.min(z,zo)-radius,
            Math.max(x,xo)+radius,Math.max(y,yo)+radius,Math.max(z,zo)+radius,spawning,
            self instanceof naturality.client.particle.WaterfallParticle);
    }
}
