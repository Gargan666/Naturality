package naturality.client.particles.sparks.mixins;

import naturality.client.particles.sparks.SparksClient;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Minecart spark emission follows client travel without adding per-tick particle network traffic. */
@NullMarked
public final class SparkClientMixins {
    private SparkClientMixins() {}

    @Mixin(AbstractMinecart.class)
    public static abstract class Minecart {
        @Unique private @Nullable Vec3 naturality$previousSparkPosition;

        @Inject(method = "tick", at = @At("TAIL"))
        private void naturality$sparks(CallbackInfo ci) {
            var cart = (AbstractMinecart)(Object)this;
            if (!cart.level().isClientSide()) return;
            SparksClient.minecart(cart, naturality$previousSparkPosition);
            naturality$previousSparkPosition = cart.position();
        }
    }
}
