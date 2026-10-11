package naturality.mixin;

import naturality.sleep.SleepAcceleration;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stat;
import net.minecraft.stats.Stats;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.StrawBedBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayer.class)
public class SleepPlayerMixin {
    @Unique private BlockPos naturality$straw;
    @Redirect(method = "startSleepInBed", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;isCreative()Z"))
    private boolean naturality$ignoreMonsters(ServerPlayer player) { return SleepAcceleration.enabled() || player.isCreative(); }
    @Redirect(method = "startSleeping", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;resetStat(Lnet/minecraft/stats/Stat;)V"))
    private void naturality$deferRest(ServerPlayer player, Stat<?> stat) {
        if (!SleepAcceleration.enabled() || !stat.equals(Stats.CUSTOM.get(Stats.TIME_SINCE_REST))) player.resetStat(stat);
    }
    @Inject(method = "startSleeping", at = @At("RETURN"))
    private void naturality$start(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue()) SleepAcceleration.started((ServerPlayer)(Object)this);
    }
    @Inject(method = "stopSleepInBed", at = @At("HEAD"))
    private void naturality$rememberStraw(boolean immediate, boolean update, CallbackInfo ci) {
        var player = (ServerPlayer)(Object)this;
        naturality$straw = SleepAcceleration.enabled() && player.isSleeping()
            ? player.getSleepingPos().filter(pos -> player.level().getBlockState(pos).getBlock() instanceof StrawBedBlock).orElse(null) : null;
    }
    @Inject(method = "stopSleepInBed", at = @At("RETURN"))
    private void naturality$wake(boolean immediate, boolean update, CallbackInfo ci) {
        var player = (ServerPlayer)(Object)this;
        SleepAcceleration.stopped(player);
        if (naturality$straw != null && player.level().getBlockState(naturality$straw).getBlock() instanceof StrawBedBlock)
            player.level().destroyBlock(naturality$straw, false);
        naturality$straw = null;
    }
}
