package naturality.client.mixin;

import naturality.weather.EndGravity;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(LocalPlayer.class)
public abstract class DistortionCreativeFlightMixin {
    @ModifyArg(method="aiStep",at=@At(value="INVOKE",target="Lnet/minecraft/world/phys/Vec3;add(DDD)Lnet/minecraft/world/phys/Vec3;"),index=1)
    private double naturality$gravityLocalFlight(double y) {
        return EndGravity.inverted((LocalPlayer)(Object)this)?-y:y;
    }
}