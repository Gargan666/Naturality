package naturality.client.systems.rotation.mixins;

import com.mojang.blaze3d.vertex.PoseStack;
import naturality.client.systems.rotation.PlayerRotationRendering;
import naturality.systems.rotation.PlayerRotation;
import naturality.systems.rotation.PlayerRotation.Angles;
import net.minecraft.client.Camera;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.debug.EntityHitboxDebugRenderer;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Camera, model and client movement hooks share the common server-owned body orientation. */
public final class RotationMixins {
    private RotationMixins() { }

    @Mixin(AvatarRenderState.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class PlayerRotationRenderStateMixin implements PlayerRotationRendering.State {
        @Unique private Angles naturality$bodyRotation = Angles.ZERO;
        @Unique private float naturality$rotationPivot;
        public Angles naturality$bodyRotation() { return naturality$bodyRotation; }
        public void naturality$bodyRotation(Angles angles) { naturality$bodyRotation = angles; }
        public float naturality$rotationPivot() { return naturality$rotationPivot; }
        public void naturality$rotationPivot(float height) { naturality$rotationPivot = height; }
    }

    @Mixin(AvatarRenderer.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class PlayerRotationRendererMixin {
        @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V", at = @At("TAIL"))
        private void naturality$extract(Avatar entity, AvatarRenderState state, float partial, CallbackInfo ci) {
            if (entity instanceof Player player) PlayerRotationRendering.extract(player, state, partial);
            else ((PlayerRotationRendering.State)state).naturality$bodyRotation(Angles.ZERO);
        }
        @Inject(method = "setupRotations(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;FF)V", at = @At("HEAD"), cancellable = true)
        private void naturality$rotate(AvatarRenderState state, PoseStack pose, float bodyYaw, float scale, CallbackInfo ci) {
            if (!((PlayerRotationRendering.State)state).naturality$bodyRotation().active()) return;
            PlayerRotationRendering.rotate(state, pose, bodyYaw, scale);
            ci.cancel();
        }
        @Inject(method = "getRenderOffset", at = @At("RETURN"), cancellable = true)
        private void naturality$offset(AvatarRenderState state, CallbackInfoReturnable<Vec3> cir) {
            var angles = ((PlayerRotationRendering.State)state).naturality$bodyRotation();
            if (angles.active()) cir.setReturnValue(angles.transform(cir.getReturnValue()));
        }
    }

    @Mixin(Camera.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class PlayerRotationCameraMixin {
        @Shadow private @Nullable Entity entity;
        @Shadow protected abstract void setPosition(Vec3 position);
        // Set the eye origin before vanilla's third-person obstruction sweep, not after that sweep.
        @Inject(method = "alignWithEntity", at = @At(value = "FIELD", target = "Lnet/minecraft/client/Camera;detached:Z", opcode = Opcodes.PUTFIELD))
        private void naturality$eyePosition(float partial, CallbackInfo ci) {
            var cameraEntity = entity;
            if (cameraEntity != null && (PlayerRotation.active(cameraEntity) || PlayerRotation.interpolatedAngles(cameraEntity, partial).active()))
                setPosition(cameraEntity.getEyePosition(partial));
        }
    }

    @Mixin(LocalPlayer.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class PlayerRotationLocalPlayerMixin {
        @Inject(method = "moveTowardsClosestSpace", at = @At("HEAD"), cancellable = true)
        private void naturality$avoidEnvelopePush(double x, double z, CallbackInfo ci) {
            var player = (LocalPlayer)(Object)this;
            // Envelope corners are not body contacts, even during a transient real overlap.
            // The oriented collision solver already permits movement out of penetration.
            if (PlayerRotation.active(player)) ci.cancel();
        }
    }

    @Mixin(EntityHitboxDebugRenderer.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class PlayerRotationDebugMixin {
        @Inject(method = "showHitboxes", at = @At("HEAD"), cancellable = true)
        private void naturality$realHitbox(Entity entity, float partial, boolean server, CallbackInfo ci) {
            if (entity instanceof Player player && PlayerRotation.active(player)) {
                PlayerRotationRendering.debug(player, partial, server);
                ci.cancel();
            }
        }
    }
}
