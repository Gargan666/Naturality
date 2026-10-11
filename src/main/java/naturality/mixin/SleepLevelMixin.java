package naturality.mixin;

import naturality.sleep.SleepAcceleration;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.players.SleepStatus;
import net.minecraft.server.level.ServerPlayer;
import java.util.List;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ServerLevel.class)
public class SleepLevelMixin {
    @Redirect(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/players/SleepStatus;areEnoughDeepSleeping(ILjava/util/List;)Z"))
    private boolean naturality$noSkip(SleepStatus status, int percentage, List<ServerPlayer> players) {
        return !SleepAcceleration.enabled() && status.areEnoughDeepSleeping(percentage, players);
    }
}
