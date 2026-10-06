package naturality.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LivingEntity.class)
public abstract class ElytraMomentumMixin {
    // Accumulate momentum while diving; spend it quickly after pulling out.
    // This operation runs in the gravity-local frame, including inverted End flight.
    @WrapOperation(method = "updateFallFlyingMovement", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/phys/Vec3;multiply(DDD)Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 naturality$retainMomentum(Vec3 movement, double x, double y, double z,
                                         Operation<Vec3> original) {
        boolean diving = movement.y < -0.25;
        Vec3 result = original.call(movement, diving ? 0.997 : 0.99, 0.98,
                diving ? 0.997 : 0.99);
        double speed = result.length();
        if (!diving && speed > 1.6) {
            // Shed 6% of excess speed per tick: about 90% is spent in two seconds.
            result = result.scale((1.6 + (speed - 1.6) * 0.94) / speed);
        }
        // 4.5 blocks/tick (90 blocks/second), shared across all flight directions.
        double limitedSpeed = result.length();
        return limitedSpeed > 4.5 ? result.scale(4.5 / limitedSpeed) : result;
    }
}
