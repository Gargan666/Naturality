package naturality.systems.sleep.mixins;

import naturality.systems.sleep.SleepAcceleration;
import net.minecraft.world.attribute.BedRule;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.stats.Stat;
import net.minecraft.stats.Stats;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.StrawBedBlock;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import naturality.systems.sleep.SleepAcceleration.SleepServerSnapshots;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.clock.ServerClockManager;

/** Feature integration hooks; each nested type retains its independent Mixin target and gate. */
public final class SleepMixins {
    private SleepMixins() {}

    @Mixin(BedRule.class)
    @org.jspecify.annotations.NullMarked
    public static class SleepBedRuleMixin {
        @Inject(method = "canSleep", at = @At("HEAD"), cancellable = true)
        private void naturality$anyTime(Level level, CallbackInfoReturnable<Boolean> cir) {
            if (SleepAcceleration.enabled() && ((BedRule)(Object)this).canSleep() == BedRule.Rule.WHEN_DARK) cir.setReturnValue(true);
        }
    }

    @Mixin(ServerLevel.class)
    @org.jspecify.annotations.NullMarked
    public static class SleepLevelMixin {
        @ModifyExpressionValue(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/players/SleepStatus;areEnoughDeepSleeping(ILjava/util/List;)Z"))
        private boolean naturality$noSkip(boolean original) {
            return original && !SleepAcceleration.enabled();
        }
    }

    @Mixin(ServerPlayer.class)
    @org.jspecify.annotations.NullMarked
    public static class SleepPlayerMixin {
        @Unique private @org.jspecify.annotations.Nullable BlockPos naturality$straw;
        @ModifyExpressionValue(method = "startSleepInBed", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;isCreative()Z"))
        private boolean naturality$ignoreMonsters(boolean original) { return original || SleepAcceleration.enabled(); }
        @WrapOperation(method = "startSleeping", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;resetStat(Lnet/minecraft/stats/Stat;)V"))
        private void naturality$deferRest(ServerPlayer player, Stat<?> stat, Operation<Void> original) {
            if (!SleepAcceleration.enabled() || !stat.equals(Stats.CUSTOM.get(Stats.TIME_SINCE_REST))) original.call(player, stat);
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
            var straw = naturality$straw;
            if (straw != null && player.level().getBlockState(straw).getBlock() instanceof StrawBedBlock)
                player.level().destroyBlock(straw, false);
            naturality$straw = null;
        }
    }

    @Mixin(MinecraftServer.class)
    @org.jspecify.annotations.NullMarked
    public static class SleepServerSnapshotsMixin {
        @WrapOperation(method = "tickChildren", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/clock/ServerClockManager;tick()V"))
        private void naturality$batchClock(ServerClockManager manager, Operation<Void> original) {
            var server = (MinecraftServer)(Object)this;
            if (SleepAcceleration.active(server)) SleepServerSnapshots.tickClocks(server);
            else original.call(manager);
        }
        @WrapOperation(method = "tickChildren", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;forceGameTimeSynchronization()V"))
        private void naturality$limitSnapshots(MinecraftServer server, Operation<Void> original) {
            if (!SleepAcceleration.active(server)) original.call(server);
        }
    }
}
