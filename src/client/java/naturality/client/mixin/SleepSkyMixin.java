package naturality.client.mixin;

import naturality.client.sleep.SleepSky;
import naturality.client.sleep.SleepVisuals;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.client.renderer.state.level.SkyRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SkyRenderer.class)
public class SleepSkyMixin {
    @Unique private Object naturality$world;
    @Unique private long naturality$lastFrame;
    @Unique private boolean naturality$easing;
    @Unique private SleepSky.Angle naturality$sun = new SleepSky.Angle();
    @Unique private SleepSky.Angle naturality$moon = new SleepSky.Angle();
    @Unique private SleepSky.Angle naturality$stars = new SleepSky.Angle();
    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void naturality$smoothSleepSky(ClientLevel level, float partial, Camera camera, SkyRenderState state, CallbackInfo ci) {
        long now = System.nanoTime();
        if (naturality$world != level) {
            naturality$world = level; naturality$easing = false; naturality$lastFrame = now;
            naturality$sun = new SleepSky.Angle(); naturality$moon = new SleepSky.Angle(); naturality$stars = new SleepSky.Angle();
        }
        double dt = Minecraft.getInstance().isPaused() ? 0 : Math.min(.25, (now - naturality$lastFrame) / 1e9);
        naturality$lastFrame = now;
        if (!SleepVisuals.accelerating() && !naturality$easing) return;
        if (!naturality$easing) {
            naturality$sun = new SleepSky.Angle(); naturality$moon = new SleepSky.Angle(); naturality$stars = new SleepSky.Angle();
        }
        naturality$easing = true;
        state.sunAngle = naturality$sun.sample(state.sunAngle, dt, SleepVisuals.accelerating());
        state.moonAngle = naturality$moon.sample(state.moonAngle, dt, SleepVisuals.accelerating());
        state.starAngle = naturality$stars.sample(state.starAngle, dt, SleepVisuals.accelerating());
        if (!SleepVisuals.accelerating() && naturality$sun.settled()) naturality$easing = false;
    }
}
