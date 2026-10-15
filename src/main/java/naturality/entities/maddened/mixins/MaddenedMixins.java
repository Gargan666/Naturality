package naturality.entities.maddened.mixins;

import net.minecraft.world.entity.monster.Creeper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import naturality.entities.maddened.MaddenedCreepers.MaddenedCreeper;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.effect.MobEffectInstance;
import naturality.bootstrap.ContentRegistration.NaturalityEffects;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import naturality.entities.maddened.MaddenedVariants.MaddenedMob;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import naturality.entities.maddened.MaddenedVariants;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.NaturalSpawner;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Feature integration hooks; each nested type retains its independent Mixin target and gate. */
public final class MaddenedMixins {
    private MaddenedMixins() {}

    @Mixin(Creeper.class)
    @org.jspecify.annotations.NullMarked
    public interface CreeperFuseAccess {
        @Accessor("maxSwell") void naturality$setFuse(int ticks);
    }

    @Mixin(Creeper.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class MaddenedCreeperCloudMixin {
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

    @Mixin(Entity.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class MaddenedSoundMixin {
        @ModifyVariable(method = "playSound(Lnet/minecraft/sounds/SoundEvent;FF)V", at = @At("HEAD"), argsOnly = true, ordinal = 1)
        private float naturality$slow(float pitch, net.minecraft.sounds.SoundEvent sound, float volume, float originalPitch) {
            if (MaddenedMob.isExplosionSound(sound.location())) return pitch;
            return (Object)this instanceof MaddenedMob maddened ? maddened.maddenedSoundPitch(pitch) : pitch;
        }
    }

    @Mixin(NaturalSpawner.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class MaddenedSpawnMixin {
        // Vanilla first validates the selected biome entry; substitute only its entity factory.
        @Redirect(method = "getMobForSpawn", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/EntityType;create(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/EntitySpawnReason;)Lnet/minecraft/world/entity/Entity;"))
        private static @org.jspecify.annotations.Nullable Entity naturality$maddened(EntityType<?> original, Level level, EntitySpawnReason reason) {
            return MaddenedVariants.forSpawn((ServerLevel)level, original).create(level, reason);
        }
    }
}
