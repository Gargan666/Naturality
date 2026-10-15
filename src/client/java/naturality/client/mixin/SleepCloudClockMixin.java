package naturality.client.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import naturality.client.sleep.SleepVisuals;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.renderer.CloudRenderer;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(CloudRenderer.class)
public class SleepCloudClockMixin {
    @WrapMethod(method = "prepare(ILnet/minecraft/client/CloudStatus;FILnet/minecraft/world/phys/Vec3;JF)V")
    private void naturality$accelerateDrift(int color, CloudStatus status, float height, int range,
            Vec3 camera, long time, float partial, Operation<Void> original) {
        // Keep the accumulated offset on waking; only ordinary cloud motion remains.
        double clock = time + (double)partial + SleepVisuals.boostSeconds() * 20;
        long whole = (long)Math.floor(clock);
        original.call(color, status, height, range, camera, whole, (float)(clock - whole));
    }
}
