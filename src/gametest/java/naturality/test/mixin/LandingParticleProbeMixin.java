package naturality.test.mixin;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerLevel.class)
public abstract class LandingParticleProbeMixin {
    @Inject(method="sendParticles(Lnet/minecraft/core/particles/ParticleOptions;DDDIDDDD)I",at=@At("HEAD"))
    private void naturality$landing(ParticleOptions options,double x,double y,double z,int count,double dx,double dy,double dz,double speed,CallbackInfoReturnable<Integer> cir) {
        if(naturality.test.DistortionInteractionGameTest.captureLanding && options.getType()==ParticleTypes.BLOCK)
            naturality.test.DistortionInteractionGameTest.landingY=y;
    }
}