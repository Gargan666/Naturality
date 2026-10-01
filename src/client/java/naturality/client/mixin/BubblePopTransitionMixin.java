package naturality.client.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.BubbleParticle;
import net.minecraft.client.particle.BubbleColumnUpParticle;
import net.minecraft.client.particle.WaterCurrentDownParticle;
import net.minecraft.core.particles.ParticleTypes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Spawn vanilla's animated, water-tinted pop whenever a bubble is removed. */
@Mixin(Particle.class)
public abstract class BubblePopTransitionMixin {
    @Unique private boolean naturality$popEmitted;

    @Inject(method = "remove", at = @At("HEAD"))
    private void naturality$popOnRemoval(CallbackInfo ci) {
        if (!naturality.config.NaturalityConfig.get().effects.bubblePops) return;
        if (!naturality$popEmitted && ((Object) this instanceof BubbleParticle
            || (Object) this instanceof BubbleColumnUpParticle
            || (Object) this instanceof WaterCurrentDownParticle)) {
            naturality$popEmitted = true;
            var level = Minecraft.getInstance().level;
            if (level != null) {
                var position = ((Particle) (Object) this).getBoundingBox().getCenter();
                level.addParticle(ParticleTypes.BUBBLE_POP,
                    position.x, position.y, position.z, 0, 0, 0);
            }
        }
    }
}


