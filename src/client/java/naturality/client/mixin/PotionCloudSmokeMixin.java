package naturality.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import naturality.NaturalityParticles;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(AreaEffectCloud.class)
public abstract class PotionCloudSmokeMixin {
    @WrapOperation(method = "clientTick", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/level/Level;addAlwaysVisibleParticle(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)V"))
    private void naturality$smoke(Level level, ParticleOptions options, double x, double y, double z,
                                  double vx, double vy, double vz, Operation<Void> original) {
        // Use the cloud color even during vanilla's white waiting-phase flashes.
        var source = ((AreaEffectCloud) (Object) this).getParticle();
        // Thin both lingering potion and dragon clouds without changing their gameplay.
        if ((source.getType() == ParticleTypes.ENTITY_EFFECT || source.getType() == ParticleTypes.DRAGON_BREATH)
                && level.getRandom().nextFloat() < 0.25F) return;
        if (source.getType() == ParticleTypes.ENTITY_EFFECT && source instanceof ColorParticleOption color) {
            original.call(level, NaturalityParticles.POTION_CLOUD, x, y, z,
                (double) color.getRed(), (double) color.getGreen(), (double) color.getBlue());
        } else if (source.getType() == ParticleTypes.DRAGON_BREATH) {
            // Explicitly mark dragon cloud smoke as stationary; flying breath uses billboards.
            var random = level.getRandom();
            original.call(level, NaturalityParticles.POTION_CLOUD, x, y, z,
                (double) (0.7176471F + random.nextFloat() * (0.8745098F - 0.7176471F)),
                0.0, (double) (0.8235294F + random.nextFloat() * (0.9764706F - 0.8235294F)));
        } else {
            original.call(level, options, x, y, z, vx, vy, vz);
        }
    }
}
