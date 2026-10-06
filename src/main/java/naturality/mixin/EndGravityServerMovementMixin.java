package naturality.mixin;

import naturality.weather.EndGravity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import com.llamalad7.mixinextras.sugar.Local;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class EndGravityServerMovementMixin {
    @Shadow public ServerPlayer player;
    @Shadow private boolean clientIsFloating;
    @Inject(method="tick",at=@At("HEAD"))
    private void naturality$allowedGravityFlight(CallbackInfo ci) {
        if(EndGravity.affected(player) && EndGravity.inversion(player,false)>0)clientIsFloating=false;
    }
    @ModifyVariable(method="handlePlayerPositionChange",at=@At("STORE"),name="movedUpwards")
    private boolean naturality$movingAwayFromSupport(boolean upwards,@Local(name="yDist") double dy) {
        // Both jump recognition and fall-counter reset must use gravity-local up.
        return EndGravity.inverted(player)?dy<0:upwards;
    }
}
