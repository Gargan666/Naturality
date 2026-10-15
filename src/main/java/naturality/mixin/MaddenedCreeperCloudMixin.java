package naturality.mixin;

import naturality.entity.MaddenedCreeper;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.effect.MobEffectInstance;
import naturality.NaturalityEffects;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Creeper.class)
public abstract class MaddenedCreeperCloudMixin {
    @Inject(method = "spawnLingeringCloud", at = @At("HEAD"), cancellable = true)
    private void naturality$insanity(CallbackInfo ci) {
        if (!((Object)this instanceof MaddenedCreeper creeper)) return;
        var cloud = new AreaEffectCloud(creeper.level(), creeper.getX(), creeper.getY(), creeper.getZ());
        cloud.setOwner(creeper);
        cloud.setRadius(3);
        cloud.setRadiusOnUse(-.5F);
        cloud.setWaitTime(10);
        cloud.setDuration(300);
        cloud.setRadiusPerTick(-cloud.getRadius() / cloud.getDuration());
        cloud.setPotionDurationScale(1);
        cloud.addEffect(new MobEffectInstance(NaturalityEffects.INSANITY, 400));
        creeper.level().addFreshEntity(cloud);
        ci.cancel();
    }
}
