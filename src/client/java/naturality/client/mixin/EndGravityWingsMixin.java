package naturality.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import naturality.weather.EndGravity;
import net.minecraft.world.entity.ElytraAnimationState;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ElytraAnimationState.class)
public abstract class EndGravityWingsMixin {
    @Shadow @Final private LivingEntity entity;
    @ModifyExpressionValue(method="tick",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/LivingEntity;getDeltaMovement()Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 naturality$localWingMotion(Vec3 movement) {
        return EndGravity.inverted(entity)?new Vec3(movement.x,-movement.y,movement.z):movement;
    }
}