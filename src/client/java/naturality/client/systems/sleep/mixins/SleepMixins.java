package naturality.client.systems.sleep.mixins;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import naturality.client.systems.sleep.SleepVisuals;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.renderer.CloudRenderer;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import net.minecraft.client.particle.Particle;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import naturality.client.systems.sleep.SleepVisuals.SleepSky;
import naturality.client.systems.sleep.SleepVisuals.SleepSnapshots;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.client.renderer.state.level.SkyRenderState;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;

/** Feature integration hooks; each nested type retains its independent Mixin target and gate. */
public final class SleepMixins {
    private SleepMixins() {}

    @Mixin(CloudRenderer.class)
    @org.jspecify.annotations.NullMarked
    public static class SleepCloudClockMixin {
        @WrapMethod(method = "prepare(ILnet/minecraft/client/CloudStatus;FILnet/minecraft/world/phys/Vec3;JF)V")
        private void naturality$accelerateDrift(int color, CloudStatus status, float height, int range,
                Vec3 camera, long time, float partial, Operation<Void> original) {
            // Keep the accumulated offset on waking; only ordinary cloud motion remains.
            double clock = time + (double)partial + SleepVisuals.boostSeconds() * 20;
            long whole = (long)Math.floor(clock);
            original.call(color, status, height, range, camera, whole, (float)(clock - whole));
        }
    }

    /** Scale movement requests, never persistent velocity, age or emitter ticking. */
    @Mixin(Particle.class)
    @org.jspecify.annotations.NullMarked
    public static class SleepParticleVelocityMixin {
        @ModifyVariable(method = "move", at = @At("HEAD"), argsOnly = true, ordinal = 0)
        private double naturality$speedX(double velocity) { return velocity * SleepVisuals.speed(); }
        @ModifyVariable(method = "move", at = @At("HEAD"), argsOnly = true, ordinal = 1)
        private double naturality$speedY(double velocity) { return velocity * SleepVisuals.speed(); }
        @ModifyVariable(method = "move", at = @At("HEAD"), argsOnly = true, ordinal = 2)
        private double naturality$speedZ(double velocity) { return velocity * SleepVisuals.speed(); }
    }

    @Mixin(SkyRenderer.class)
    @org.jspecify.annotations.NullMarked
    public static class SleepSkyMixin {
        @Unique private @org.jspecify.annotations.Nullable Object naturality$world;
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
            boolean accelerating = SleepVisuals.accelerating();
            if (!accelerating && !naturality$easing) return;
            if (!naturality$easing) {
                naturality$sun = new SleepSky.Angle(); naturality$moon = new SleepSky.Angle(); naturality$stars = new SleepSky.Angle();
            }
            var snapshot = SleepSnapshots.latest(level);
            if (accelerating && snapshot == null) return;
            naturality$easing = true;
            state.sunAngle = naturality$sun.sample(accelerating && snapshot != null ? snapshot.sun() : state.sunAngle, snapshot == null ? 0 : snapshot.revision(), dt, accelerating);
            state.moonAngle = naturality$moon.sample(accelerating && snapshot != null ? snapshot.moon() : state.moonAngle, snapshot == null ? 0 : snapshot.revision(), dt, accelerating);
            state.starAngle = naturality$stars.sample(accelerating && snapshot != null ? snapshot.stars() : state.starAngle, snapshot == null ? 0 : snapshot.revision(), dt, accelerating);
            if (!accelerating && naturality$sun.settled()) naturality$easing = false;
        }
    }

    @Mixin(ClientPacketListener.class)
    @org.jspecify.annotations.NullMarked
    public static class SleepTimeSnapshotMixin {
        @Inject(method = "handleSetTime", at = @At("TAIL"))
        private void naturality$captureTime(ClientboundSetTimePacket packet, CallbackInfo ci) {
            SleepSnapshots.capture(packet.gameTime());
        }
    }
}
