package naturality.client.particles.smoke.mixins;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import naturality.client.particles.smoke.SmokeParticle.FireSmoke;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.jspecify.annotations.Nullable;

/** Client-only source tracking and replacement of normal fire's individual smoke emissions. */
public final class SmokeMixins {
    private SmokeMixins() { }

    @Mixin(net.minecraft.client.particle.SingleQuadParticle.class)
    public static abstract class SmokeFacingMixin extends net.minecraft.client.particle.Particle {
        protected SmokeFacingMixin(net.minecraft.client.multiplayer.ClientLevel level, double x, double y, double z) {
            super(level, x, y, z);
        }

        @WrapOperation(method = "extract", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/particle/SingleQuadParticle$FacingCameraMode;setRotation(Lorg/joml/Quaternionf;Lnet/minecraft/client/Camera;F)V"))
        private void naturality$facePosition(net.minecraft.client.particle.SingleQuadParticle.FacingCameraMode mode,
                org.joml.Quaternionf rotation, net.minecraft.client.Camera camera, float partialTick,
                Operation<Void> original) {
            if (!naturality.client.particles.smoke.SmokeParticle.facesCameraPosition(this)) {
                original.call(mode, rotation, camera, partialTick);
                return;
            }
            var eye = camera.position();
            naturality.client.particles.smoke.SmokeParticle.faceCameraPosition(rotation,
                eye.x - net.minecraft.util.Mth.lerp(partialTick, xo, x),
                eye.y - net.minecraft.util.Mth.lerp(partialTick, yo, y),
                eye.z - net.minecraft.util.Mth.lerp(partialTick, zo, z));
        }
    }

    @Mixin(BaseFireBlock.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class FireSmokeEmissionMixin {
        @WrapOperation(method = "animateTick", at = @At(value = "INVOKE",
                target = "Lnet/minecraft/world/level/Level;addParticle(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)V"))
        private void naturality$sharedSmoke(Level level, ParticleOptions options, double x, double y, double z,
                double vx, double vy, double vz, Operation<Void> original, @Local(argsOnly = true) BlockState state,
                @Local(argsOnly = true) BlockPos pos) {
            if (!state.is(Blocks.FIRE) || !FireSmoke.usesPlume(level, pos))
                original.call(level, options, x, y, z, vx, vy, vz);
        }
    }

    @Mixin(LevelChunk.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class FireSmokeTrackingMixin {
        @Inject(method = "setBlockState", at = @At("RETURN"))
        private void naturality$track(BlockPos pos, BlockState state, int flags,
                CallbackInfoReturnable<@Nullable BlockState> cir) {
            FireSmoke.changed((LevelChunk) (Object) this, pos, cir.getReturnValue());
        }
    }

    @Mixin(net.minecraft.client.particle.ParticleEngine.class)
    public static abstract class SmokeParticleLifecycleMixin {
        @Inject(method = "clearParticles", at = @At("RETURN"))
        private void naturality$clear(org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
            FireSmoke.particlesCleared();
            naturality.client.blocks.fire.FireAsh.particlesCleared();
        }
    }
}
