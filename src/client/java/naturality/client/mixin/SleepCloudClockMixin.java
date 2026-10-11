package naturality.client.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import naturality.client.sleep.SleepCloudClock;
import naturality.client.sleep.SleepVisuals;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.CloudRenderer;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(CloudRenderer.class)
public class SleepCloudClockMixin {
    @Unique private SleepCloudClock naturality$clock = new SleepCloudClock();
    @Unique private Object naturality$world;
    @Unique private long naturality$lastFrame;
    @WrapMethod(method = "prepare(ILnet/minecraft/client/CloudStatus;FILnet/minecraft/world/phys/Vec3;JF)V")
    private void naturality$easeDrift(int color, CloudStatus status, float height, int range,
            Vec3 camera, long time, float partial, Operation<Void> original) {
        var client = Minecraft.getInstance();
        long now = System.nanoTime();
        if (naturality$world != client.level) {
            naturality$world = client.level; naturality$clock = new SleepCloudClock(); naturality$lastFrame = now;
        }
        double dt = client.isPaused() || naturality$lastFrame == 0 ? 0 : Math.min(.25, (now - naturality$lastFrame) / 1e9);
        naturality$lastFrame = now;
        double eased = naturality$clock.sample(time + (double)partial, dt, SleepVisuals.accelerating());
        long whole = (long)Math.floor(eased);
        original.call(color, status, height, range, camera, whole, (float)(eased - whole));
    }
}
