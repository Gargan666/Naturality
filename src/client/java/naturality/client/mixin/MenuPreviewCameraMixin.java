package naturality.client.mixin;

import naturality.client.menu.MenuPreview;
import naturality.client.menu.MenuWorld;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;

import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Camera.class)
public abstract class MenuPreviewCameraMixin {
    @Inject(method = "getFluidInCamera", at = @At("HEAD"), cancellable = true)
    private void naturality$noImmersion(CallbackInfoReturnable<net.minecraft.world.level.material.FogType> cir) {
        if (MenuPreview.active()) cir.setReturnValue(net.minecraft.world.level.material.FogType.NONE);
    }
    @Shadow protected abstract void setPosition(Vec3 position);
    @Shadow protected abstract void setRotation(float yaw, float pitch);
    @Shadow public abstract Matrix4f getViewRotationMatrix(Matrix4f matrix);
    @Invoker("prepareCullFrustum") protected abstract void naturality$frustum(Matrix4fc view, Matrix4f projection, Vec3 position);
    @Inject(method = "update", at = @At("RETURN"))
    private void naturality$orbit(DeltaTracker delta, CallbackInfo ci) {
        if (!MenuPreview.active()) return;
        var client = Minecraft.getInstance();
        var orbit = MenuWorld.camera();
        orbit.frame(client.options.panoramaSpeed().get());
        double yaw = Math.toRadians(orbit.yaw()), pitch = Math.toRadians(orbit.pitch());
        var center = MenuPreview.spawn();
        var position = new Vec3(center.getX() - Math.sin(yaw) * Math.cos(pitch) * 180,
                center.getY() + 30 + Math.sin(pitch) * 180,
                center.getZ() + Math.cos(yaw) * Math.cos(pitch) * 180);
        setPosition(position); setRotation(180 + orbit.yaw(), orbit.pitch());
        // Include the camera's section cube conservatively; the native perspective
        // frustum-offset loop cannot safely chase an orthographic near plane.
        naturality$frustum(getViewRotationMatrix(new Matrix4f()), MenuPreview.cullingProjection(), position);
    }
    @Inject(method = "extractRenderState", at = @At("RETURN"))
    private void naturality$projection(CameraRenderState state, DeltaTracker delta, CallbackInfo ci) {
        if (!MenuPreview.active()) return;
        state.projectionMatrix.set(MenuPreview.projection());
        state.isFirstPerson = false; state.smartCull = false;
    }
    @Inject(method = "getViewRotationProjectionMatrix", at = @At("HEAD"), cancellable = true)
    private void naturality$viewProjection(Matrix4f matrix, CallbackInfoReturnable<Matrix4f> ci) {
        if (MenuPreview.active()) ci.setReturnValue(MenuPreview.projection().mul(getViewRotationMatrix(new Matrix4f()), matrix));
    }
}
