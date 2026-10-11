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
public abstract class EndGravityServerMovementMixin implements naturality.weather.GravityMovementAccess {
    @Shadow private double firstGoodY;
    @Shadow private double lastGoodY;
    public void naturality$poseShift(double shift) {firstGoodY+=shift;lastGoodY+=shift;}
    @Inject(method={"handlePlayerInput","handlePlayerCommand"},at=@At("TAIL"))
    private void naturality$poseBeforeMovement(CallbackInfo ci) {
        if(EndGravity.inverted(player))((naturality.weather.GravityPoseAccess)player).naturality$updateGravityPose();
    }
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
