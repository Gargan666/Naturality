package naturality.client.mixin;

import naturality.client.particle.PortalGlowController;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(NetherPortalBlock.class)
public abstract class NetherPortalBlockMixin {
    @Inject(method = "animateTick", at = @At("HEAD"), cancellable = true)
    private void naturality$waitForReveal(BlockState state, Level level, BlockPos pos, RandomSource random, CallbackInfo ci) {
        if (naturality.client.portal.PortalOpeningClient.isOpening(pos)) ci.cancel();
    }
    @WrapOperation(method = "animateTick", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/level/Level;addParticle(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)V"))
    private void naturality$replacePortalMote(Level level, ParticleOptions type,
            double x, double y, double z, double vx, double vy, double vz, Operation<Void> original,
            @Local(argsOnly = true) BlockPos pos, @Local(argsOnly = true) BlockState state) {
        if (naturality.config.NaturalityConfig.get().portalChanges.portalParticleChanges && type == ParticleTypes.PORTAL && level instanceof ClientLevel clientLevel) {
            PortalGlowController.spawnMote(clientLevel, pos, state, x, y, z, vx, vy, vz);
        } else {
            original.call(level, type, x, y, z, vx, vy, vz);
        }
    }

    @Inject(method = "animateTick", at = @At("TAIL"))
    private void naturality$discoverPortal(BlockState state, Level level, BlockPos pos,
                                           RandomSource random, CallbackInfo ci) {
        if (level instanceof ClientLevel clientLevel) {
            PortalGlowController.observe(clientLevel, pos, state);
        }
    }
}
