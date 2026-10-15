package naturality.mixin;

import naturality.sleep.SleepAcceleration;
import naturality.sleep.SleepServerSnapshots;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.clock.ServerClockManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(MinecraftServer.class)
public class SleepServerSnapshotsMixin {
    @Redirect(method = "tickChildren", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/clock/ServerClockManager;tick()V"))
    private void naturality$batchClock(ServerClockManager manager) {
        SleepServerSnapshots.tickClocks((MinecraftServer)(Object)this);
    }
    @Redirect(method = "tickChildren", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;forceGameTimeSynchronization()V"))
    private void naturality$limitSnapshots(MinecraftServer server) {
        if (!SleepAcceleration.active(server)) server.forceGameTimeSynchronization();
    }
}
