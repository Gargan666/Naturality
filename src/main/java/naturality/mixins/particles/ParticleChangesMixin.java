package naturality.mixins.particles;

import naturality.bootstrap.ContentRegistration.NaturalityParticles;
import naturality.config.startup.FeatureConfig.Feature;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import net.minecraft.world.entity.boss.enderdragon.phases.DragonDeathPhase;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import naturality.particles.explosions.EndExplosionContext;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Feature integration hooks; each nested type retains its independent Mixin target and gate. */
public final class ParticleChangesMixin {
    private ParticleChangesMixin() {}

    @Mixin(net.minecraft.world.entity.projectile.ShulkerBullet.class)
    public static abstract class ShulkerBulletExplosionMixin {
        @org.spongepowered.asm.mixin.injection.ModifyArg(method = "onHitBlock", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerLevel;sendParticles(Lnet/minecraft/core/particles/ParticleOptions;DDDIDDDD)I"), index = 0)
        private ParticleOptions naturality$vanillaImpact(ParticleOptions particle) {
            return Feature.EXPLOSION_PARTICLES.enabled() && particle == ParticleTypes.EXPLOSION
                ? NaturalityParticles.SHULKER_BULLET_EXPLOSION : particle;
        }
    }

    @Mixin(EnderDragon.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class DragonDeathExplosionMixin {
        @Redirect(method = {"aiStep", "tickDeath"}, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;addParticle(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)V"))
        private void naturality$endDeathBurst(Level level, ParticleOptions particle,
                double x, double y, double z, double vx, double vy, double vz) {
            if (Feature.EXPLOSION_PARTICLES.enabled()
                    && (particle.getType() == ParticleTypes.EXPLOSION || particle.getType() == ParticleTypes.EXPLOSION_EMITTER)) {
                particle = NaturalityParticles.END_EXPLOSION;
            }
            level.addParticle(particle, x, y, z, vx, vy, vz);
        }
    }

    /** Recolor the dying dragon's bursts while it flies toward the podium. */
    @Mixin(DragonDeathPhase.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class DragonDeathFlightExplosionMixin {
        @Redirect(method = "doClientTick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;addParticle(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)V"))
        private void naturality$endFlightBurst(Level level, ParticleOptions particle,
                double x, double y, double z, double vx, double vy, double vz) {
            if (Feature.EXPLOSION_PARTICLES.enabled()
                    && (particle.getType() == ParticleTypes.EXPLOSION || particle.getType() == ParticleTypes.EXPLOSION_EMITTER)) {
                particle = NaturalityParticles.END_EXPLOSION;
            }
            level.addParticle(particle, x, y, z, vx, vy, vz);
        }
    }

    /** Scope the End palette to pillar rebuilding and ritual crystal detonation. */
    @Mixin(targets = {"net.minecraft.world.level.dimension.end.DragonRespawnStage$3",
        "net.minecraft.world.level.dimension.end.DragonRespawnStage$4"})
    @org.jspecify.annotations.NullMarked
    public static abstract class DragonRitualExplosionMixin {
        @WrapOperation(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerLevel;explode(Lnet/minecraft/world/entity/Entity;DDDFLnet/minecraft/world/level/Level$ExplosionInteraction;)V"))
        private void naturality$ritualPalette(ServerLevel level, @Nullable Entity source,
                double x, double y, double z, float strength, Level.ExplosionInteraction interaction, Operation<Void> original) {
            if (Feature.EXPLOSION_PARTICLES.enabled()) {
                EndExplosionContext.run(() -> original.call(level, source, x, y, z, strength, interaction));
            } else {
                original.call(level, source, x, y, z, strength, interaction);
            }
        }
    }

    /** Identify crystal explosions before their source is discarded on the client. */
    @Mixin(ServerLevel.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class EndCrystalExplosionMixin {
        @ModifyVariable(method = "explode", at = @At("HEAD"), argsOnly = true, ordinal = 0)
        private ParticleOptions naturality$smallCrystalExplosion(ParticleOptions particle, @Local(argsOnly = true) @Nullable Entity source) {
            return (source instanceof EndCrystal || source == null && naturality.particles.explosions.EndExplosionContext.active())
                && Feature.EXPLOSION_PARTICLES.enabled() && particle.getType() == ParticleTypes.EXPLOSION
                ? NaturalityParticles.END_EXPLOSION : particle;
        }

        @ModifyVariable(method = "explode", at = @At("HEAD"), argsOnly = true, ordinal = 1)
        private ParticleOptions naturality$largeCrystalExplosion(ParticleOptions particle, @Local(argsOnly = true) @Nullable Entity source) {
            return (source instanceof EndCrystal || source == null && naturality.particles.explosions.EndExplosionContext.active())
                && Feature.EXPLOSION_PARTICLES.enabled() && particle.getType() == ParticleTypes.EXPLOSION_EMITTER
                ? NaturalityParticles.END_EXPLOSION : particle;
        }
    }

    @Mixin(BlockItem.class)
    @org.jspecify.annotations.NullMarked
    public static class SandPlacementParticlesMixin {
        @Inject(method = "placeBlock", at = @At("RETURN"))
        private void naturality$sandBurst(BlockPlaceContext context, BlockState state, CallbackInfoReturnable<Boolean> cir) {
            if (cir.getReturnValue() && (state.is(Blocks.SAND) || state.is(Blocks.RED_SAND))
                    && context.getLevel() instanceof ServerLevel level) {
                // Dedicated placement event broadcasts one outside-face burst to nearby clients.
                level.levelEvent(0x4E5301, context.getClickedPos(), Block.getId(state));
            }
        }
    }
}
