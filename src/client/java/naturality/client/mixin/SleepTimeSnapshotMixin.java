package naturality.client.mixin;
import naturality.client.sleep.SleepSnapshots;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(ClientPacketListener.class)
public class SleepTimeSnapshotMixin {
    @Inject(method = "handleSetTime", at = @At("TAIL"))
    private void naturality$captureTime(ClientboundSetTimePacket packet, CallbackInfo ci) {
        SleepSnapshots.capture(packet.gameTime());
    }
}
